package com.agv.algorithm.sim;

import com.agv.algorithm.model.Arc;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.path.AStarPlanner;
import com.agv.algorithm.path.PathResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 离散时间步仿真引擎：每 tick 推进 100 ms，逐段滚动预约消解冲突。
 * <p>
 * 关键机制（对应实施计划 3.2）：
 * <ol>
 *   <li>AGV 每次只预约下一段弧与目标节点，不预留全程；</li>
 *   <li>物理路段同一时刻只允许一个方向使用（边级预约，消除相向冲突）；</li>
 *   <li>同一资源竞争者按 AGV 优先级让行（小号优先），从根上避免同资源配置抖动；</li>
 *   <li>连续等待超过阈值 → 带禁行约束重规划；等待环检测作为兜底，检测到环就禁掉争议路段。</li>
 * </ol>
 * 引擎是纯 Java、单线程、给定 seed 可复现的，不依赖 Spring。
 */
public final class SimulationEngine {

    private static final double EPS = 1e-9;

    /** 车辆离开路口后保留的清空 tick，避免车尾还在路口时后车压上来。 */
    private static final int NODE_CLEARANCE_TICKS = 2;

    /** 任务全部完成后，回充宽限期（tick）。超时即收尾，保证仿真一定能结束。 */
    private static final long PARK_GRACE_TICKS = 1200;

    /** 全部车辆连续等待超过该 tick，判定为无法解开的死锁。 */
    private static final long DEADLOCK_WAIT_TICKS = 2000;

    /** 还有任务未完成，但连续这么久没有任何任务完成，判定为活锁或死锁。 */
    private static final long DEADLOCK_NO_PROGRESS_TICKS = 10_000;

    private final RoadNet net;
    private final List<Task> allTasks;
    private final SimConfig config;
    private final TaskAssigner assigner;
    private final AStarPlanner planner = AStarPlanner.defaultPlanner();
    private final ReservationTable reservations = new ReservationTable();
    private final Map<String, Agv> agvs = new LinkedHashMap<>();
    private final List<Task> pending = new ArrayList<>();
    private final Map<String, String> edgeOwner = new HashMap<>();
    private final List<String> chargers;

    private long tick;
    private int completedTasks;
    private long makespan;
    private long tasksDoneTick = -1;
    private double totalPathLen;
    private long totalWaitTicks;
    private int conflictCount;
    private int replanCount;
    private int deadlockBreakCount;
    private int retreatSuccessCount;
    private int maxBlockedTicks;
    private boolean graceFinished;
    private int unparkedAtFinish;
    private boolean finished;
    private boolean deadlocked;
    private long allWaitingTicks;
    private long lastCycleEventTick = -DEADLOCK_WAIT_TICKS;
    private long lastCompletionTick;

    public SimulationEngine(RoadNet net, List<Task> tasks, SimConfig config) {
        if (net == null || net.isEmpty()) {
            throw new IllegalArgumentException("路网为空，无法启动仿真");
        }
        if (config.agvCount() < 1) {
            throw new IllegalArgumentException("AGV 数量至少为 1");
        }
        this.net = net;
        this.config = config;
        this.allTasks = new ArrayList<>(tasks == null ? List.of() : tasks);
        this.allTasks.sort(Comparator.comparingInt(Task::id));
        this.assigner = TaskAssigners.of(config.algorithm(), net, planner);

        this.chargers = net.nodes().values().stream()
                .filter(n -> n.type() == NodeType.CHARGER)
                .map(NetNode::id)
                .toList();
        if (chargers.isEmpty()) {
            throw new IllegalArgumentException("路网没有充电位，AGV 无处初始化");
        }

        for (Task task : allTasks) {
            validateTask(task);
        }
        this.pending.addAll(allTasks);

        for (int i = 0; i < config.agvCount(); i++) {
            String charger = chargers.get(i % chargers.size());
            Agv agv = new Agv("AGV-" + (i + 1), i + 1, charger);
            agvs.put(agv.id, agv);
            if (reservations.canReserve(charger, 0, Interval.FOREVER, nodeCapacity(charger))) {
                reservations.reserve(agv.id, charger, 0, Interval.FOREVER);
            }
        }
    }

    private void validateTask(Task task) {
        if (net.node(task.from()) == null) {
            throw new IllegalArgumentException("任务 " + task.id() + " 的起点不存在：" + task.from());
        }
        if (net.node(task.to()) == null) {
            throw new IllegalArgumentException("任务 " + task.id() + " 的终点不存在：" + task.to());
        }
        if (!planner.plan(net, task.from(), task.to()).found()) {
            throw new IllegalArgumentException("任务 " + task.id() + " 的起终点不可达：" + task.from() + " → " + task.to());
        }
    }

    /** 推进一个时间步，返回该帧数据。 */
    public SimFrame step() {
        List<SimEvent> events = new ArrayList<>();
        if (finished || deadlocked) {
            return frame(events);
        }
        tick++;

        for (Agv agv : agvs.values()) {
            if (finished) {
                break;
            }
            if (agv.status == AgvStatus.LOADING) {
                if (--agv.serviceTicksLeft <= 0) {
                    startDelivery(agv, events);
                }
                continue;
            }
            if (agv.status == AgvStatus.UNLOADING) {
                if (--agv.serviceTicksLeft <= 0) {
                    completeTask(agv, events);
                }
                continue;
            }
            switch (agv.status) {
                case MOVING, REVERSING -> advance(agv, events);
                case IDLE -> onIdleAtNode(agv, events);
                case WAITING -> onWaiting(agv, events);
                default -> {
                }
            }
        }

        if (tick % 10 == 0) {
            detectWaitCycle(events);
        }

        totalWaitTicks += agvs.values().stream().filter(a -> a.status == AgvStatus.WAITING).count();
        updateFinished(events);
        checkDeadlock(events);
        if (tick % 100 == 0) {
            reservations.prune(tick);
        }
        return frame(events);
    }

    /** 离线跑完一轮，返回指标。 */
    public SimulationResult runToCompletion() {
        long guard = 0;
        while (!finished && !deadlocked && guard++ < config.maxTicks()) {
            step();
        }
        // 路径长由逐 tick 累加得到，避免把浮点尾差带进报表
        double roundedLength = Math.round(totalPathLen * 10.0) / 10.0;
        return new SimulationResult(makespan, roundedLength, totalWaitTicks,
                conflictCount, completedTasks, finished, deadlocked, tick);
    }

    public List<AgvSnapshot> snapshots() {
        return agvs.values().stream().map(this::snapshot).toList();
    }

    public boolean finished() {
        return finished;
    }

    /** 是否因无法解开的等待环而停止。 */
    public boolean deadlocked() {
        return deadlocked;
    }

    public long tick() {
        return tick;
    }

    public SimFrame.Metrics metrics() {
        return new SimFrame.Metrics(makespan, totalPathLen, totalWaitTicks,
                conflictCount, completedTasks, allTasks.size());
    }

    /** 诊断计数：只用于日志，不参与指标口径。 */
    public SimDiagnostics diagnostics() {
        return new SimDiagnostics(replanCount, deadlockBreakCount, retreatSuccessCount,
                maxBlockedTicks, graceFinished, unparkedAtFinish);
    }

    // ---------------------------------------------------------------- 运动

    private void advance(Agv agv, List<SimEvent> events) {
        Arc arc = agv.currentArc;
        double delta = arc.speed() * config.deltaSeconds();
        if (agv.retreating) {
            double step = Math.min(delta, agv.s);
            agv.s -= step;
            agv.totalDistance += step;
            totalPathLen += step;
            if (agv.s <= EPS) {
                agv.s = 0;
                tryFinishRetreat(agv, events);
            }
            return;
        }
        double remaining = arc.length() - agv.s;
        double step = Math.min(delta, remaining);
        agv.s += step;
        agv.totalDistance += step;
        totalPathLen += step;
        if (agv.s + EPS >= arc.length()) {
            agv.s = arc.length();
            tryCrossNode(agv, events);
        }
    }

    /** 倒车到当前弧起点后，尝试进入起点节点；进不去就保持占用并等待。 */
    private void tryFinishRetreat(Agv agv, List<SimEvent> events) {
        Arc arc = agv.currentArc;
        String from = arc.from();
        if (!reservations.canReserve(from, tick, Interval.FOREVER,
                nodeCapacity(from), agv.id)) {
            if (agv.status != AgvStatus.WAITING) {
                conflictCount++;
                events.add(new SimEvent(tick, agv.id, SimEvent.Type.WAIT_CONFLICT, arc.id(),
                        "节点 " + from + " 被占用（倒车等待）"));
            }
            agv.status = AgvStatus.WAITING;
            agv.wantedNodeId = from;
            return;
        }
        reservations.reserve(agv.id, from, tick, Interval.FOREVER);
        releaseArc(agv, arc);
        agv.currentArc = null;
        agv.currentNode = from;
        agv.s = 0;
        agv.retreating = false;
        agv.lastArc = null;
        agv.status = AgvStatus.MOVING;
        afterNodeArrival(agv, events);
    }

    /**
     * 到达当前弧末端。只有在能同时预约目标节点（以及路口的下一段弧）时才真正进入节点，
     * 否则停在停线上等待，避免把车停进路口或工位。
     */
    private void tryCrossNode(Agv agv, List<SimEvent> events) {
        Arc current = agv.currentArc;
        if (current == null) {
            return;
        }
        String nodeId = current.to();
        boolean wasWaiting = agv.status == AgvStatus.WAITING;

        if (agv.path.isEmpty()) {
            String reason = null;
            if (!reservations.canReserve(nodeId, tick, Interval.FOREVER,
                    nodeCapacity(nodeId), agv.id)) {
                reason = "节点 " + nodeId + " 被占用（节点冲突）";
            }
            if (reason != null) {
                blockAtStopLine(agv, current, current.id(), nodeId, reason, events);
                return;
            }
            if (!reservations.holdersAt(nodeId, tick).contains(agv.id)) {
                reservations.reserve(agv.id, nodeId, tick, Interval.FOREVER);
            }
            releaseArc(agv, current);
            agv.currentNode = nodeId;
            agv.currentArc = null;
            agv.s = 0;
            agv.lastArc = current;
            clearWait(agv, events, current.id(), wasWaiting);
            afterNodeArrival(agv, events);
            return;
        }

        Arc next = agv.path.peek();
        String nextNode = next.to();
        boolean nextIsGoal = agv.path.size() == 1;
        String reason = arcBlockedReason(agv, next);
        if (reason == null && !reservations.canReserve(nodeId, tick,
                tick + NODE_CLEARANCE_TICKS, nodeCapacity(nodeId), agv.id)) {
            reason = "节点 " + nodeId + " 被占用（节点冲突）";
        }
        if (reason == null && nextIsGoal && !reservations.canReserve(nextNode, tick,
                Interval.FOREVER, nodeCapacity(nextNode), agv.id)) {
            reason = "节点 " + nextNode + " 被占用（节点冲突）";
        }
        if (reason != null) {
            blockAtStopLine(agv, current, next.id(), nextNode, reason, events);
            return;
        }

        // 原子地通过路口：短暂占用节点、持有下一段弧；若下一节点是终点则一并预约。
        releaseArc(agv, current);
        reservations.reserve(agv.id, nodeId, tick, tick + NODE_CLEARANCE_TICKS);
        reservations.reserve(agv.id, next.id(), tick, Interval.FOREVER);
        if (nextIsGoal) {
            reservations.reserve(agv.id, nextNode, tick, Interval.FOREVER);
        }
        if (!isDualLane(next)) {
            edgeOwner.put(next.edgeId(), agv.id);
        }
        agv.path.poll();
        agv.currentNode = nodeId;
        agv.currentArc = next;
        agv.s = 0;
        agv.status = AgvStatus.MOVING;
        agv.lastArc = current;
        clearWait(agv, events, next.id(), wasWaiting);
    }

    private void blockAtStopLine(Agv agv, Arc current, String place, String nextNode,
                                 String reason, List<SimEvent> events) {
        if (agv.status != AgvStatus.WAITING) {
            conflictCount++;
            events.add(new SimEvent(tick, agv.id, SimEvent.Type.WAIT_CONFLICT, place, reason));
        }
        agv.status = AgvStatus.WAITING;
        agv.wantedEdgeId = agv.path.isEmpty() ? null : agv.path.peek().edgeId();
        agv.wantedNodeId = nextNode;
    }

    private void clearWait(Agv agv, List<SimEvent> events, String place, boolean wasWaiting) {
        if (wasWaiting && agv.waitTicks > 0) {
            events.add(new SimEvent(tick, agv.id, SimEvent.Type.WAIT_RESOLVED, place,
                    "等待 " + agv.waitTicks + " tick 后结束"));
        }
        agv.waitTicks = 0;
        agv.blockedTicks = 0;
    }

    private void releaseArc(Agv agv, Arc arc) {
        reservations.release(arc.id(), agv.id);
        if (agv.id.equals(edgeOwner.get(arc.edgeId()))) {
            edgeOwner.remove(arc.edgeId());
        }
    }

    private String locationNode(Agv agv) {
        return agv.currentArc == null ? agv.currentNode : agv.currentArc.to();
    }

    private String arcBlockedReason(Agv agv, Arc arc) {
        if (!isDualLane(arc)) {
            String owner = edgeOwner.get(arc.edgeId());
            if (owner != null && !owner.equals(agv.id)) {
                return "路段 " + arc.edgeId() + " 已被 " + owner + " 占用（相向冲突）";
            }
        }
        if (!reservations.canReserve(arc.id(), tick, Interval.FOREVER,
                capacityOf(arc), agv.id)) {
            return "弧 " + arc.id() + " 容量已满（追尾冲突）";
        }
        return null;
    }

    private void afterNodeArrival(Agv agv, List<SimEvent> events) {
        if (agv.retreatGoal != null) {
            String goal = agv.retreatGoal;
            agv.retreatGoal = null;
            agv.path.clear();
            planAndProceed(agv, goal, events);
            return;
        }
        if (!agv.path.isEmpty()) {
            tryEnterNext(agv, events);
            return;
        }
        agv.wantedEdgeId = null;
        agv.wantedNodeId = null;
        switch (agv.leg) {
            case PICKUP -> {
                if (agv.currentNode.equals(agv.task.from())) {
                    startLoading(agv, events);
                } else {
                    planAndProceed(agv, agv.task.from(), events);
                }
            }
            case DELIVERY -> {
                if (agv.currentNode.equals(agv.task.to())) {
                    startUnloading(agv, events);
                } else {
                    planAndProceed(agv, agv.task.to(), events);
                }
            }
            case NONE -> {
                if (agv.returning) {
                    if (chargers.contains(agv.currentNode)) {
                        agv.returning = false;
                        agv.status = AgvStatus.CHARGING;
                        events.add(new SimEvent(tick, agv.id, SimEvent.Type.CHARGING,
                                agv.currentNode, "停靠充电位（无电量模型，充电时长未建模）"));
                    } else {
                        String charger = nearestFreeCharger(agv.currentNode);
                        if (charger == null) {
                            agv.status = AgvStatus.DONE;
                        } else {
                            planAndProceed(agv, charger, events);
                        }
                    }
                } else {
                    agv.status = AgvStatus.IDLE;
                }
            }
        }
    }

    private void startLoading(Agv agv, List<SimEvent> events) {
        agv.status = AgvStatus.LOADING;
        agv.serviceTicksLeft = Math.max(1, config.loadTicks());
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.LOADING,
                agv.currentNode, "开始装货，任务 " + agv.task.id()));
    }

    private void startDelivery(Agv agv, List<SimEvent> events) {
        agv.leg = Leg.DELIVERY;
        planAndProceed(agv, agv.task.to(), events);
    }

    private void startUnloading(Agv agv, List<SimEvent> events) {
        agv.status = AgvStatus.UNLOADING;
        agv.serviceTicksLeft = Math.max(1, config.unloadTicks());
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.UNLOADING,
                agv.currentNode, "开始卸货，任务 " + agv.task.id()));
    }

    private void completeTask(Agv agv, List<SimEvent> events) {
        completedTasks++;
        makespan = tick;
        lastCompletionTick = tick;
        if (completedTasks >= allTasks.size()) {
            tasksDoneTick = tick;
        }
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.UNLOADED,
                agv.currentNode, "卸货完成，耗时 " + Math.max(1, config.unloadTicks()) + " tick"));
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.TASK_DONE,
                agv.currentNode, "任务 " + agv.task.id() + " 完成"));
        agv.task = null;
        agv.leg = Leg.NONE;
        agv.status = AgvStatus.IDLE;
    }

    // ---------------------------------------------------------------- 任务分配

    private void onIdleAtNode(Agv agv, List<SimEvent> events) {
        if (agv.status != AgvStatus.IDLE) {
            return;
        }
        if (pending.isEmpty()) {
            // 没有可接任务：回充电位待命，避免占住路口/工位堵死其他车
            returnToCharger(agv, events);
            return;
        }
        Task chosen = assigner.choose(agv.currentNode, pending);
        if (chosen == null) {
            return;
        }
        pending.remove(chosen);
        agv.task = chosen;
        agv.leg = Leg.PICKUP;
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.TASK_ASSIGNED,
                agv.currentNode, "任务 " + chosen.id() + "：" + chosen.from() + " → " + chosen.to()));
        planAndProceed(agv, chosen.from(), events);
    }

    private void returnToCharger(Agv agv, List<SimEvent> events) {
        if (chargers.contains(agv.currentNode)) {
            agv.status = AgvStatus.CHARGING;
            return;
        }
        String charger = nearestFreeCharger(agv.currentNode);
        if (charger == null) {
            agv.status = AgvStatus.DONE;
            return;
        }
        agv.returning = true;
        agv.leg = Leg.NONE;
        planAndProceed(agv, charger, events);
    }

    private void planAndProceed(Agv agv, String goal, List<SimEvent> events) {
        agv.wantedEdgeId = null;
        agv.wantedNodeId = null;
        if (agv.currentNode.equals(goal)) {
            afterNodeArrival(agv, events);
            return;
        }
        PathResult result = planner.plan(net, agv.currentNode, goal, agv.bannedEdges);
        if (!result.found() && !agv.bannedEdges.isEmpty()) {
            // 禁行只是"尽量绕开"的提示；绕不出去时必须放弃禁行，否则会永久卡死
            agv.bannedEdges.clear();
            result = planner.plan(net, agv.currentNode, goal, agv.bannedEdges);
        }
        if (!result.found()) {
            // 起终点在设计期已校验可达，这里只可能是临时封锁导致，降级为等待
            agv.status = AgvStatus.WAITING;
            return;
        }
        agv.path.clear();
        agv.path.addAll(result.arcs());
        tryEnterNext(agv, events);
    }

    // ---------------------------------------------------------------- 预约

    private boolean tryEnterNext(Agv agv, List<SimEvent> events) {
        if (agv.path.isEmpty()) {
            afterNodeArrival(agv, events);
            return true;
        }
        boolean wasWaiting = agv.status == AgvStatus.WAITING;
        Arc next = agv.path.peek();
        boolean nextIsGoal = agv.path.size() == 1;
        String nextNode = next.to();
        String reason = arcBlockedReason(agv, next);
        if (reason == null && nextIsGoal
                && !reservations.canReserve(nextNode, tick, Interval.FOREVER,
                nodeCapacity(nextNode), agv.id)) {
            reason = "节点 " + nextNode + " 被占用（节点冲突）";
        }
        if (reason != null) {
            if (agv.status != AgvStatus.WAITING) {
                conflictCount++;
                events.add(new SimEvent(tick, agv.id, SimEvent.Type.WAIT_CONFLICT,
                        next.id(), reason));
            }
            // 停在节点上等待：节点由本车持有，其他车不会叠进来。
            agv.status = AgvStatus.WAITING;
            agv.wantedEdgeId = next.edgeId();
            agv.wantedNodeId = nextNode;
            return false;
        }

        reservations.reserve(agv.id, next.id(), tick, Interval.FOREVER);
        if (nextIsGoal) {
            reservations.reserve(agv.id, nextNode, tick, Interval.FOREVER);
        }
        releaseCurrentNode(agv);
        if (!isDualLane(next)) {
            edgeOwner.put(next.edgeId(), agv.id);
        }
        agv.path.poll();
        agv.currentArc = next;
        agv.s = 0;
        agv.status = AgvStatus.MOVING;
        clearWait(agv, events, next.id(), wasWaiting);
        agv.wantedEdgeId = null;
        agv.wantedNodeId = null;
        agv.bannedEdges.clear();
        return true;
    }

    private void onWaiting(Agv agv, List<SimEvent> events) {
        agv.waitTicks++;
        agv.blockedTicks++;
        maxBlockedTicks = Math.max(maxBlockedTicks, agv.blockedTicks);
        if (agv.waitTicks > 0 && agv.waitTicks % Math.max(1, config.waitTimeoutTicks()) == 0) {
            replanWithDetour(agv, events);
            return;
        }
        if (agv.currentArc != null) {
            if (agv.retreating) {
                tryFinishRetreat(agv, events);
            } else {
                tryCrossNode(agv, events);
            }
        } else {
            tryEnterNext(agv, events);
        }
    }

    private void replanWithDetour(Agv agv, List<SimEvent> events) {
        Arc wanted = agv.path.peek();
        if (wanted == null) {
            return;
        }
        String goal = currentGoal(agv);
        if (goal == null) {
            return;
        }
        Set<String> blocked = new HashSet<>(agv.bannedEdges);
        blocked.add(wanted.edgeId());
        PathResult result = planner.plan(net, locationNode(agv), goal, blocked);
        if (result.found() && !result.arcs().isEmpty()) {
            int waited = agv.waitTicks;
            agv.path.clear();
            agv.path.addAll(result.arcs());
            agv.bannedEdges.add(wanted.edgeId());
            agv.waitTicks = 0;
            replanCount++;
            events.add(new SimEvent(tick, agv.id, SimEvent.Type.REPLAN,
                    wanted.id(), "等待 " + waited + " tick 后绕行重规划，禁行 " + wanted.edgeId()));
        } else {
            agv.waitTicks = 0;
            if (!agv.bannedEdges.isEmpty()) {
                agv.bannedEdges.clear();
            }
        }
    }

    private String currentGoal(Agv agv) {
        if (agv.task != null) {
            return agv.leg == Leg.PICKUP ? agv.task.from() : agv.task.to();
        }
        if (agv.returning) {
            return nearestCharger(locationNode(agv), Set.of());
        }
        return null;
    }

    /**
     * 等待环检测：把"我在等谁"建成有向图，有环就让环上编号最小的车禁掉争议路段并重规划。
     * 这是兜底手段，正常情况下优先级让行已经避免了环。
     */
    private void detectWaitCycle(List<SimEvent> events) {
        Map<String, Set<String>> waitFor = new LinkedHashMap<>();
        for (Agv agv : agvs.values()) {
            if (agv.status != AgvStatus.WAITING || agv.path.isEmpty()) {
                continue;
            }
            Arc next = agv.path.peek();
            Set<String> blockers = new HashSet<>();
            if (!isDualLane(next)) {
                String owner = edgeOwner.get(next.edgeId());
                if (owner != null) {
                    blockers.add(owner);
                }
            }
            blockers.addAll(reservations.holdersAt(next.id(), tick));
            blockers.addAll(reservations.holdersAt(next.to(), tick));
            if (agv.currentArc != null) {
                blockers.addAll(reservations.holdersAt(agv.currentArc.to(), tick));
            }
            blockers.remove(agv.id);
            waitFor.put(agv.id, blockers);
        }
        if (waitFor.isEmpty()) {
            return;
        }
        for (String start : waitFor.keySet()) {
            List<String> cycle = findCycle(start, waitFor);
            if (cycle.isEmpty()) {
                continue;
            }
            List<Agv> members = cycle.stream()
                    .map(agvs::get)
                    .sorted(Comparator.comparingInt(agv -> agv.priority))
                    .toList();
            for (Agv member : members) {
                if (member != null && tryRetreat(member, events)) {
                    lastCycleEventTick = tick;
                    return;
                }
            }
            for (Agv member : members) {
                if (member != null && tryBackOff(member, events)) {
                    lastCycleEventTick = tick;
                    return;
                }
            }
            Agv first = members.isEmpty() ? null : members.get(0);
            if (first == null) {
                return;
            }
            if (tick - lastCycleEventTick >= 100) {
                deadlockBreakCount++;
                lastCycleEventTick = tick;
                Arc wanted = first.path.peek();
                events.add(new SimEvent(tick, first.id, SimEvent.Type.DEADLOCK_BREAK,
                        wanted == null ? "-" : wanted.id(), "检测到等待环，无法倒车让行"));
            }
            replanWithDetour(first, events);
            return;
        }
    }

    /**
     * 倒车让行：退回到来时的那条弧，把当前节点让给对方。
     * 单车道走廊上两车互不相让时，这是唯一能打破僵局的动作。
     */
    /**
     * 停线倒车：沿当前弧退回起点，让出前方节点/路段。
     * 与节点倒车不同，这里不瞬间改变位置，而是按原速度倒车，过程可在 3D 中看到。
     */
    private boolean tryBackOff(Agv agv, List<SimEvent> events) {
        if (agv.currentArc == null || agv.retreating || agv.s <= 0) {
            return false;
        }
        String goal = currentGoal(agv);
        if (goal == null) {
            return false;
        }
        agv.retreatGoal = goal;
        agv.path.clear();
        agv.retreating = true;
        agv.status = AgvStatus.REVERSING;
        agv.waitTicks = 0;
        agv.blockedTicks = 0;
        retreatSuccessCount++;
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.DEADLOCK_BREAK,
                agv.currentArc.id(), "倒车让行至 " + agv.currentArc.from()));
        return true;
    }

    private boolean tryRetreat(Agv agv, List<SimEvent> events) {
        if (agv.currentArc != null) {
            // 停在弧末端的车不能在本模型里倒车，只能等待或绕行。
            return false;
        }
        Arc last = agv.lastArc;
        if (last == null) {
            return false;
        }
        String goal = currentGoal(agv);
        if (goal == null) {
            return false;
        }
        String reverseId = last.id().endsWith("+") ? last.edgeId() + "-" : last.edgeId() + "+";
        Arc reverse = net.arc(reverseId);
        if (reverse == null || arcBlockedReason(agv, reverse) != null) {
            return false;
        }

        reservations.release(agv.currentNode, agv.id);
        reservations.reserve(agv.id, reverse.id(), tick, Interval.FOREVER);
        if (!isDualLane(reverse)) {
            edgeOwner.put(reverse.edgeId(), agv.id);
        }
        agv.path.clear();
        agv.currentArc = reverse;
        agv.s = 0;
        agv.status = AgvStatus.MOVING;
        agv.lastArc = null;
        agv.retreatGoal = goal;
        agv.waitTicks = 0;
        agv.blockedTicks = 0;
        retreatSuccessCount++;
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.DEADLOCK_BREAK,
                reverse.id(), "倒车让行至 " + reverse.to()));
        return true;
    }

    private List<String> findCycle(String start, Map<String, Set<String>> graph) {
        List<String> path = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        return dfsCycle(start, start, graph, path, visiting);
    }

    private List<String> dfsCycle(String current, String target, Map<String, Set<String>> graph,
                                  List<String> path, Set<String> visiting) {
        if (!visiting.add(current)) {
            return List.of();
        }
        path.add(current);
        for (String next : graph.getOrDefault(current, Set.of())) {
            if (next.equals(target)) {
                return new ArrayList<>(path);
            }
            if (graph.containsKey(next)) {
                List<String> found = dfsCycle(next, target, graph, path, visiting);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        path.remove(path.size() - 1);
        visiting.remove(current);
        return List.of();
    }

    // ---------------------------------------------------------------- 收尾

    private void updateFinished(List<SimEvent> events) {
        if (completedTasks < allTasks.size()) {
            return;
        }
        boolean allParked = agvs.values().stream()
                .allMatch(a -> a.status == AgvStatus.CHARGING || a.status == AgvStatus.DONE);
        // 充电位数量可能少于车辆数，允许一段回充宽限期后收尾，避免因停不进去而永不结束
        boolean graceExpired = tasksDoneTick >= 0 && tick - tasksDoneTick > PARK_GRACE_TICKS;
        if (allParked || graceExpired) {
            finished = true;
            graceFinished = graceExpired && !allParked;
            unparkedAtFinish = (int) agvs.values().stream()
                    .filter(a -> a.status != AgvStatus.CHARGING && a.status != AgvStatus.DONE)
                    .count();
            String detail = graceFinished
                    ? "全部任务完成，用时 " + makespan + " tick；宽限结束，" + unparkedAtFinish + " 台未归位"
                    : "全部任务完成，用时 " + makespan + " tick；归位 "
                    + (agvs.size() - unparkedAtFinish) + "/" + agvs.size() + " 台";
            events.add(new SimEvent(tick, "-", SimEvent.Type.FINISHED, "-", detail));
        }
    }

    /**
     * 死锁判定：全部车辆都在等待，且连续 {@link #DEADLOCK_WAIT_TICKS} 没有推进。
     * 任务未完成时才判定；任务完成后的回充排队交给宽限期收尾。
     */
    private void checkDeadlock(List<SimEvent> events) {
        if (finished || completedTasks >= allTasks.size()) {
            allWaitingTicks = 0;
            return;
        }
        if (tick - lastCompletionTick > DEADLOCK_NO_PROGRESS_TICKS) {
            deadlocked = true;
            events.add(new SimEvent(tick, "-", SimEvent.Type.DEADLOCK, "-",
                    "连续 " + DEADLOCK_NO_PROGRESS_TICKS + " tick 没有任务完成，判定为死锁"));
            return;
        }
        boolean allWaiting = !agvs.isEmpty()
                && agvs.values().stream().allMatch(agv -> agv.status == AgvStatus.WAITING);
        if (!allWaiting) {
            allWaitingTicks = 0;
            return;
        }
        allWaitingTicks++;
        if (allWaitingTicks > DEADLOCK_WAIT_TICKS) {
            deadlocked = true;
            events.add(new SimEvent(tick, "-", SimEvent.Type.DEADLOCK, "-",
                    "全部车辆连续等待 " + DEADLOCK_WAIT_TICKS + " tick，判定为死锁"));
        }
    }

    private SimFrame frame(List<SimEvent> events) {
        return new SimFrame(tick, snapshots(), events, finished, deadlocked, metrics());
    }

    private AgvSnapshot snapshot(Agv agv) {
        double x;
        double y;
        double heading;
        if (agv.currentArc != null) {
            Arc arc = agv.currentArc;
            NetNode from = net.node(arc.from());
            NetNode to = net.node(arc.to());
            double ratio = arc.length() <= 0 ? 0 : Math.min(1.0, agv.s / arc.length());
            x = from.x() + (to.x() - from.x()) * ratio;
            y = from.y() + (to.y() - from.y()) * ratio;
            heading = agv.retreating
                    ? Math.toDegrees(Math.atan2(from.y() - to.y(), from.x() - to.x()))
                    : Math.toDegrees(Math.atan2(to.y() - from.y(), to.x() - from.x()));
        } else {
            NetNode node = net.node(agv.currentNode);
            x = node == null ? 0 : node.x();
            y = node == null ? 0 : node.y();
            heading = 0;
        }
        if (heading < 0) {
            heading += 360;
        }
        return new AgvSnapshot(agv.id, agv.status,
                agv.currentArc == null ? null : agv.currentArc.id(),
                agv.s, x, y, heading,
                agv.task == null ? -1 : agv.task.id(), agv.waitTicks, agv.currentNode);
    }

    private int capacityOf(Arc arc) {
        NetEdge edge = net.edge(arc.edgeId());
        if (edge == null) {
            return 1;
        }
        return edge.isDualLane() ? Math.max(1, edge.capacity()) : 1;
    }

    private boolean isDualLane(Arc arc) {
        NetEdge edge = net.edge(arc.edgeId());
        return edge != null && edge.isDualLane();
    }

    private int nodeCapacity(String nodeId) {
        NetNode node = net.node(nodeId);
        return node == null ? 1 : Math.max(1, node.capacity());
    }

    private boolean isJunction(String nodeId) {
        NetNode node = net.node(nodeId);
        return node != null && node.type() == NodeType.JUNCTION;
    }

    /** 离开当前节点：释放占用；路口额外保留短暂清空时间。 */
    private void releaseCurrentNode(Agv agv) {
        reservations.release(agv.currentNode, agv.id);
        if (isJunction(agv.currentNode) && NODE_CLEARANCE_TICKS > 0) {
            reservations.reserve(agv.id, agv.currentNode, tick, tick + NODE_CLEARANCE_TICKS);
        }
    }

    /** 就近挑一个没人占的充电位，避免两台车抢同一个车位形成新的僵局。 */
    private String nearestFreeCharger(String fromNode) {
        String best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (String charger : chargers) {
            if (!charger.equals(fromNode) && !reservations.isFree(charger)) {
                continue;
            }
            PathResult result = planner.plan(net, fromNode, charger);
            if (result.found() && result.costSeconds() < bestCost) {
                bestCost = result.costSeconds();
                best = charger;
            }
        }
        return best != null ? best : nearestCharger(fromNode, Set.of());
    }

    private String nearestCharger(String fromNode, Set<String> blocked) {
        String best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (String charger : chargers) {
            PathResult result = planner.plan(net, fromNode, charger, blocked);
            if (result.found() && result.costSeconds() < bestCost) {
                bestCost = result.costSeconds();
                best = charger;
            }
        }
        return best;
    }

    private enum Leg {
        NONE, PICKUP, DELIVERY
    }

    private static final class Agv {
        final String id;
        final int priority;
        String currentNode;
        Arc currentArc;
        Arc lastArc;
        double s;
        Deque<Arc> path = new ArrayDeque<>();
        Task task;
        Leg leg = Leg.NONE;
        AgvStatus status = AgvStatus.IDLE;
        int serviceTicksLeft;
        int waitTicks;
        int blockedTicks;
        String wantedEdgeId;
        String wantedNodeId;
        String retreatGoal;
        boolean returning;
        boolean retreating;
        double totalDistance;
        final Set<String> bannedEdges = new HashSet<>();

        Agv(String id, int priority, String currentNode) {
            this.id = id;
            this.priority = priority;
            this.currentNode = currentNode;
        }
    }

    /** 便于测试与调试：当前所有 AGV 状态。 */
    public List<String> debugStates() {
        List<String> list = new ArrayList<>();
        for (Agv agv : agvs.values()) {
            list.add(agv.id + ":" + agv.status
                    + "@" + (agv.currentArc == null ? agv.currentNode : agv.currentArc.id())
                    + "[task=" + (agv.task == null ? "-" : agv.task.id())
                    + ",leg=" + agv.leg
                    + ",path=" + agv.path.stream().map(Arc::id).toList()
                    + ",want=" + agv.wantedEdgeId
                    + ",ret=" + agv.returning
                    + ",ban=" + agv.bannedEdges
                    + "]");
        }
        return list;
    }

    public Optional<Task> taskOf(String agvId) {
        Agv agv = agvs.get(agvId);
        return agv == null ? Optional.empty() : Optional.ofNullable(agv.task);
    }
}
