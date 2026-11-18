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
import java.util.Random;
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
    private static final long PARK_GRACE_TICKS = 400;

    /** 严格容量下连续没有任务完成多久判定为死锁。 */
    private static final long DEADLOCK_TIMEOUT_TICKS = 2000;

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
    private final Random random;

    private long tick;
    private int completedTasks;
    private long makespan;
    private long tasksDoneTick = -1;
    private long lastTaskDoneTick;
    private boolean deadlocked;
    private double totalPathLen;
    private long totalWaitTicks;
    private int conflictCount;
    private boolean finished;
    /** 最后一单完成时冻结报表口径；之后的回充与停靠只影响动画，不再推高指标。 */
    private boolean metricsFrozen;
    private double frozenPathLen;
    private long frozenWaitTicks;
    private int frozenConflictCount;
    /** 任务分配时刻，用于算单均配送时长；任务完成时移除。 */
    private final Map<Integer, Long> assignedTicks = new HashMap<>();
    private long deliveryTicksSum;

    public SimulationEngine(RoadNet net, List<Task> tasks, SimConfig config) {
        if (net == null || net.isEmpty()) {
            throw new IllegalArgumentException("路网为空，无法启动仿真");
        }
        if (config.agvCount() < 1) {
            throw new IllegalArgumentException("AGV 数量至少为 1");
        }
        this.net = net;
        this.config = config;
        this.random = new Random(config.seed());
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

        List<String> slots = new ArrayList<>();
        int assigned = 0;
        int round = 0;
        while (assigned < config.agvCount()) {
            boolean progressed = false;
            for (String charger : chargers) {
                if (slots.size() >= config.agvCount()) {
                    break;
                }
                if (round < nodeCapacity(charger)) {
                    slots.add(charger);
                    assigned++;
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
            round++;
        }
        if (slots.size() < config.agvCount()) {
            throw new IllegalArgumentException("充电位只能停放 " + slots.size() + " 台 AGV，"
                    + "当前配置 " + config.agvCount() + " 台");
        }

        for (int i = 0; i < slots.size(); i++) {
            String charger = slots.get(i);
            Agv agv = new Agv("AGV-" + (i + 1), i + 1, charger);
            agvs.put(agv.id, agv);
            reservations.reserve(agv.id, charger, 0, Interval.FOREVER);
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
        if (finished) {
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
            switch (agv.status) {
                case MOVING -> advance(agv, events);
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
        if (!metricsFrozen && tasksDoneTick == tick) {
            frozenPathLen = totalPathLen;
            frozenWaitTicks = totalWaitTicks;
            frozenConflictCount = conflictCount;
            metricsFrozen = true;
        }
        updateFinished(events);
        if (!finished && completedTasks < allTasks.size()
                && tick - lastTaskDoneTick >= DEADLOCK_TIMEOUT_TICKS) {
            deadlocked = true;
            finished = true;
            events.add(new SimEvent(tick, "-", SimEvent.Type.FINISHED, "-",
                    "检测到死锁：连续 " + DEADLOCK_TIMEOUT_TICKS + " tick 没有任务完成，完成 "
                            + completedTasks + "/" + allTasks.size(), -1));
        }
        return frame(events);
    }

    /** 离线跑完一轮，返回指标。 */
    public SimulationResult runToCompletion() {
        long guard = 0;
        while (!finished && guard++ < config.maxTicks()) {
            step();
        }
        // 路径长由逐 tick 累加得到，避免把浮点尾差带进报表
        double roundedLength = Math.round(reportedPathLen() * 10.0) / 10.0;
        return new SimulationResult(makespan, roundedLength, reportedWaitTicks(),
                reportedConflictCount(), completedTasks, avgDeliveryTick(), finished, deadlocked, tick);
    }

    public List<AgvSnapshot> snapshots() {
        return agvs.values().stream().map(this::snapshot).toList();
    }

    public boolean finished() {
        return finished;
    }

    public boolean deadlocked() {
        return deadlocked;
    }

    public long tick() {
        return tick;
    }

    public SimFrame.Metrics metrics() {
        return new SimFrame.Metrics(makespan, reportedPathLen(), reportedWaitTicks(),
                reportedConflictCount(), completedTasks, allTasks.size(), avgDeliveryTick());
    }

    private double reportedPathLen() {
        return metricsFrozen ? frozenPathLen : totalPathLen;
    }

    private long reportedWaitTicks() {
        return metricsFrozen ? frozenWaitTicks : totalWaitTicks;
    }

    private int reportedConflictCount() {
        return metricsFrozen ? frozenConflictCount : conflictCount;
    }

    /** 单均配送时长 = Σ(完成 tick − 分配 tick) ÷ 已完成任务数；没有已完成任务记 0。 */
    private long avgDeliveryTick() {
        if (completedTasks == 0) {
            return 0;
        }
        return Math.round((double) deliveryTicksSum / completedTasks);
    }

    /** 事件上的任务号；车辆没有任务时统一记 -1。 */
    private static int taskIdOf(Agv agv) {
        return agv.task == null ? -1 : agv.task.id();
    }

    // ---------------------------------------------------------------- 运动

    private void advance(Agv agv, List<SimEvent> events) {
        Arc arc = agv.currentArc;
        double delta = arc.speed() * config.deltaSeconds();
        double remaining = arc.length() - agv.s;
        double step = Math.min(delta, remaining);
        agv.s += step;
        agv.totalDistance += step;
        totalPathLen += step;
        if (agv.s + EPS >= arc.length()) {
            arrive(agv, events);
        }
    }

    private void arrive(Agv agv, List<SimEvent> events) {
        Arc arc = agv.currentArc;
        reservations.release(arc.id(), agv.id);
        if (agv.id.equals(edgeOwner.get(arc.edgeId()))) {
            edgeOwner.remove(arc.edgeId());
        }
        agv.lastArc = arc;
        agv.currentArc = null;
        agv.s = 0;
        agv.currentNode = arc.to();
        afterNodeArrival(agv, events);
    }

    private void afterNodeArrival(Agv agv, List<SimEvent> events) {
        if (agv.yielding) {
            if (agv.currentNode.equals(agv.yieldStation)) {
                // 从缓存位回到工位，恢复让位前的剩余路径，避免重新规划后又撞回同一个瓶颈
                agv.yielding = false;
                agv.yieldStation = null;
                agv.yieldBuffer = null;
                if (agv.yieldPath != null) {
                    agv.path.clear();
                    agv.path.addAll(agv.yieldPath);
                    agv.yieldPath = null;
                }
            } else {
                // 刚驶入缓存位：停在这里等工位空出来
                agv.status = AgvStatus.WAITING;
                agv.waitTicks = 0;
                return;
            }
        }
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
                    completeTask(agv, events);
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
                                agv.currentNode, "回充完成", -1));
                    } else {
                        String charger = nearestChargerForReturn(agv);
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
        agv.serviceTicksLeft = Math.max(1, config.serviceTicks());
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.LOADING,
                agv.currentNode, "开始装货，任务 " + agv.task.id(), agv.task.id()));
    }

    private void startDelivery(Agv agv, List<SimEvent> events) {
        agv.leg = Leg.DELIVERY;
        planAndProceed(agv, agv.task.to(), events);
    }

    private void completeTask(Agv agv, List<SimEvent> events) {
        completedTasks++;
        makespan = tick;
        lastTaskDoneTick = tick;
        Long assignedTick = assignedTicks.remove(agv.task.id());
        if (assignedTick != null) {
            deliveryTicksSum += tick - assignedTick;
        }
        if (completedTasks >= allTasks.size()) {
            tasksDoneTick = tick;
        }
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.TASK_DONE,
                agv.currentNode, "任务 " + agv.task.id() + " 完成", agv.task.id()));
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
        assignedTicks.put(chosen.id(), tick);
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.TASK_ASSIGNED,
                agv.currentNode, "任务 " + chosen.id() + "：" + chosen.from() + " → " + chosen.to(),
                chosen.id()));
        planAndProceed(agv, chosen.from(), events);
    }

    private void returnToCharger(Agv agv, List<SimEvent> events) {
        if (chargers.contains(agv.currentNode)) {
            agv.status = AgvStatus.CHARGING;
            return;
        }
        String charger = nearestChargerForReturn(agv);
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
            // 起终点在设计期已校验可达，这里只可能是临时封锁导致，降级为等待；保留节点预约
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
        Arc next = agv.path.peek();
        boolean dualLane = isDualLane(next);
        String destNode = next.to();
        long travel = travelTicks(next);
        long enter = tick;
        long arrival = tick + travel;
        long arcExit = arrival + 1;
        String blockedReason = blockedReason(agv, next);

        if (blockedReason != null) {
            // 路口是临界区：等待时保持占用，避免车还停在路口却把资源让给后车。
            // 非路口只有在「另一台车确实要进这个节点」时才让位；否则留在原地并保留预约，
            // 避免对下游路段的阻塞也做无意义的工位 ↔ 缓存位往返。
            if (!isJunction(agv.currentNode) && yieldToBuffer(agv, events)) {
                return false;
            }
            // 严格容量：没有真的离开节点就不释放预约，避免「车还在工位上、预约已经让给别人」
            // 造成的物理超容。缓存位被占时，车就在原地等待，后面的车按容量排队。
            // 让位是真实移动，不是停车；只有真正进入 WAITING 才记一次冲突。
            if (agv.status != AgvStatus.WAITING) {
                conflictCount++;
                events.add(new SimEvent(tick, agv.id, SimEvent.Type.WAIT_CONFLICT,
                        next.id(), blockedReason, taskIdOf(agv)));
            }
            agv.status = AgvStatus.WAITING;
            agv.wantedEdgeId = next.edgeId();
            agv.wantedNodeId = destNode;
            return false;
        }

        releaseCurrentNode(agv);
        reservations.reserve(agv.id, next.id(), enter, arcExit);
        reservations.reserve(agv.id, destNode, arrival, Interval.FOREVER);
        if (!dualLane) {
            edgeOwner.put(next.edgeId(), agv.id);
        }

        agv.path.poll();
        agv.currentArc = next;
        agv.s = 0;
        agv.status = AgvStatus.MOVING;
        agv.waitTicks = 0;
        agv.wantedEdgeId = null;
        agv.wantedNodeId = null;
        agv.bannedEdges.clear();
        return true;
    }

    /** 进入下一条弧前要同时满足的三类预约；返回 null 表示可以进入。 */
    private String blockedReason(Agv agv, Arc next) {
        long travel = travelTicks(next);
        long enter = tick;
        long arrival = tick + travel;
        long arcExit = arrival + 1;
        String destNode = next.to();

        String owner = isDualLane(next) ? null : edgeOwner.get(next.edgeId());
        if (owner != null && !owner.equals(agv.id)) {
            return "路段 " + next.edgeId() + " 已被 " + owner + " 占用（相向冲突）";
        }
        if (!reservations.canReserve(next.id(), enter, arcExit, capacityOf(next), agv.id)) {
            return "弧 " + next.id() + " 容量已满（追尾冲突）";
        }
        if (!reservations.canReserve(destNode, arrival, Interval.FOREVER,
                nodeCapacity(destNode), agv.id)) {
            return "节点 " + destNode + " 被占用（节点冲突）";
        }
        return null;
    }

    private void onWaiting(Agv agv, List<SimEvent> events) {
        agv.waitTicks++;
        if (agv.yielding) {
            tryLeaveBuffer(agv);
            return;
        }
        if (agv.waitTicks > 0 && agv.waitTicks % Math.max(1, config.waitTimeoutTicks()) == 0) {
            replanWithDetour(agv, events);
            return;
        }
        tryEnterNext(agv, events);
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
        PathResult result = planner.plan(net, agv.currentNode, goal, blocked);
        if (result.found() && !result.arcs().isEmpty()) {
            agv.path.clear();
            agv.path.addAll(result.arcs());
            agv.bannedEdges.add(wanted.edgeId());
            agv.waitTicks = 0;
            events.add(new SimEvent(tick, agv.id, SimEvent.Type.REPLAN,
                    wanted.id(), "等待超时，绕行重规划", taskIdOf(agv)));
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
            if (completedTasks < allTasks.size()) {
                return nearestCharger(agv.currentNode, Set.of());
            }
            return nearestChargerWithRoom(agv);
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
            // 结构性保证：边的持有者一定是行驶中的车，会有限时间内释放，因此环不会长期存在。
            // 这里保留环检测作为诊断与论文中的兜底说明，检出后让编号最小的车带禁行约束重规划。
            Agv first = cycle.stream()
                    .map(agvs::get)
                    .min(Comparator.comparingInt(a -> a.priority))
                    .orElse(null);
            if (first == null || first.path.isEmpty()) {
                return;
            }
            Arc wanted = first.path.peek();
            first.bannedEdges.add(wanted.edgeId());
            first.waitTicks = 0;
            events.add(new SimEvent(tick, first.id, SimEvent.Type.DEADLOCK_BREAK,
                    wanted.id(), "检测到等待环，尝试倒车让行", taskIdOf(first)));
            if (!tryRetreat(first, events)) {
                // 严格容量下不把脚下的路口让给后车；倒车不可行时只重规划，实在无路可走由死锁超时收尾。
                replanWithDetour(first, events);
            }
            return;
        }
    }

    /**
     * 让位到相邻缓存位：把工位空出来，车停在缓存位等工位重新可用。
     *
     * <p>只有缓存位与连接边同时空闲时才可行；否则返回 false，退回「逻辑让位」。
     * 驶入与驶出都是真实移动，里程照常累加。
     */
    private boolean yieldToBuffer(Agv agv, List<SimEvent> events) {
        if (agv.yielding) {
            return false;
        }
        String station = agv.currentNode;
        String buffer = adjacentFreeBuffer(station);
        if (buffer == null) {
            return false;
        }
        Arc out = arcFromTo(station, buffer);
        if (out == null) {
            return false;
        }
        long travel = travelTicks(out);
        long arrival = tick + travel;

        releaseCurrentNode(agv);
        reservations.reserve(agv.id, out.id(), tick, arrival + 1);
        reservations.reserve(agv.id, buffer, arrival, Interval.FOREVER);
        if (!isDualLane(out)) {
            edgeOwner.put(out.edgeId(), agv.id);
        }

        agv.yieldPath = new ArrayDeque<>(agv.path);
        agv.path.clear();
        agv.wantedEdgeId = null;
        agv.wantedNodeId = null;
        agv.currentArc = out;
        agv.s = 0;
        agv.status = AgvStatus.MOVING;
        agv.yielding = true;
        agv.yieldStation = station;
        agv.yieldBuffer = buffer;
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.YIELD_BUFFER, buffer,
                "驶入缓存位 " + buffer + " 让开工位 " + station, taskIdOf(agv)));
        return true;
    }

    /** 从缓存位回工位：工位与连接边同时可预约才走，否则继续等。 */
    private void tryLeaveBuffer(Agv agv) {
        String station = agv.yieldStation;
        String buffer = agv.yieldBuffer;
        if (station == null || buffer == null) {
            agv.yielding = false;
            return;
        }
        if (!reservations.canReserve(station, tick, Interval.FOREVER, nodeCapacity(station), agv.id)) {
            return;
        }
        Arc back = arcFromTo(buffer, station);
        if (back == null) {
            return;
        }
        long travel = travelTicks(back);
        long arrival = tick + travel;
        String owner = isDualLane(back) ? null : edgeOwner.get(back.edgeId());
        if (owner != null && !owner.equals(agv.id)) {
            return;
        }
        if (!reservations.canReserve(back.id(), tick, arrival + 1, capacityOf(back), agv.id)) {
            return;
        }
        // 只放开工位不够：下一段还走不了就回工位，会立刻引发第二次让位。
        Arc next = agv.yieldPath == null ? null : agv.yieldPath.peek();
        if (next != null && blockedReason(agv, next) != null) {
            return;
        }
        reservations.release(buffer, agv.id);
        reservations.reserve(agv.id, station, arrival, Interval.FOREVER);
        reservations.reserve(agv.id, back.id(), tick, arrival + 1);
        if (!isDualLane(back)) {
            edgeOwner.put(back.edgeId(), agv.id);
        }
        agv.currentArc = back;
        agv.s = 0;
        agv.status = AgvStatus.MOVING;
        agv.waitTicks = 0;
    }

    /** 工位旁空闲且可进入的缓存位；没有返回 null。 */
    private String adjacentFreeBuffer(String station) {
        for (Arc arc : net.arcsFrom(station)) {
            NetNode node = net.node(arc.to());
            if (node == null || node.type() != NodeType.BUFFER) {
                continue;
            }
            if (arcFromTo(node.id(), station) == null) {
                continue;
            }
            if (!reservations.canReserve(node.id(), tick, Interval.FOREVER, nodeCapacity(node.id()))) {
                continue;
            }
            String owner = isDualLane(arc) ? null : edgeOwner.get(arc.edgeId());
            if (owner != null) {
                continue;
            }
            if (!reservations.canReserve(arc.id(), tick, tick + travelTicks(arc) + 1, capacityOf(arc))) {
                continue;
            }
            return node.id();
        }
        return null;
    }

    private Arc arcFromTo(String from, String to) {
        for (Arc arc : net.arcsFrom(from)) {
            if (arc.to().equals(to)) {
                return arc;
            }
        }
        return null;
    }

    /**
     * 倒车让行：退回到来时的那条弧，把当前节点让给对方。
     * 单车道走廊上两车互不相让时，这是唯一能打破僵局的动作。
     */
    private boolean tryRetreat(Agv agv, List<SimEvent> events) {
        Arc last = agv.lastArc;
        if (last == null) {
            return false;
        }
        String reverseId = last.id().endsWith("+") ? last.edgeId() + "-" : last.edgeId() + "+";
        Arc reverse = net.arc(reverseId);
        if (reverse == null) {
            return false;
        }
        String owner = isDualLane(reverse) ? null : edgeOwner.get(reverse.edgeId());
        if (owner != null && !owner.equals(agv.id)) {
            return false;
        }
        long travel = travelTicks(reverse);
        long arrival = tick + travel;
        long arcExit = arrival + 1;
        if (!reservations.canReserve(reverse.id(), tick, arcExit, capacityOf(reverse), agv.id)) {
            return false;
        }
        if (!reservations.canReserve(reverse.to(), arrival, Interval.FOREVER,
                nodeCapacity(reverse.to()), agv.id)) {
            return false;
        }

        String goal = currentGoal(agv);
        releaseCurrentNode(agv);
        reservations.reserve(agv.id, reverse.id(), tick, arcExit);
        reservations.reserve(agv.id, reverse.to(), arrival, Interval.FOREVER);
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
        events.add(new SimEvent(tick, agv.id, SimEvent.Type.DEADLOCK_BREAK,
                reverse.id(), "倒车让行至 " + reverse.to(), taskIdOf(agv)));
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
            events.add(new SimEvent(tick, "-", SimEvent.Type.FINISHED, "-",
                    "全部任务完成，用时 " + makespan + " tick", -1));
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
            heading = Math.toDegrees(Math.atan2(to.y() - from.y(), to.x() - from.x()));
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

    private long travelTicks(Arc arc) {
        double seconds = arc.length() / arc.speed();
        return Math.max(1, (long) Math.ceil(seconds / config.deltaSeconds()));
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

    /**
     * 回充选位。任务还在执行时沿用原来的最近充电位，避免回充车流改变任务阶段的路径与指标；
     * 全部任务完成后才按剩余容量选位，保证最后一台车不会一直等一个已经停满的充电位。
     */
    private String nearestChargerForReturn(Agv agv) {
        if (completedTasks < allTasks.size()) {
            return nearestFreeCharger(agv.currentNode);
        }
        return nearestChargerWithRoom(agv);
    }

    /** 任务阶段沿用原来的「最近空充电位」选法，保持论文的任务阶段数据不变。 */
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

    /**
     * 就近挑一个还有剩余容量的充电位。不能只看 {@code isFree()}：容量 4 的充电位停了 3 台仍可再进一台，
     * 否则最后一台车会一直排队等一个永远不满的空位。全部满时才退回最近的充电位排队。
     */
    private String nearestChargerWithRoom(Agv agv) {
        String fromNode = agv.currentNode;
        String best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (String charger : chargers) {
            if (!charger.equals(fromNode)
                    && !reservations.canReserve(charger, tick, Interval.FOREVER,
                    nodeCapacity(charger), agv.id)) {
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
        String wantedEdgeId;
        String wantedNodeId;
        String retreatGoal;
        boolean yielding;
        String yieldStation;
        String yieldBuffer;
        /** 让位前的剩余路径；回到工位后恢复。 */
        Deque<Arc> yieldPath;
        boolean returning;
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
