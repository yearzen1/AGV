package com.agv.algorithm.sim;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineTest {

    private static NetNode node(String id, NodeType type, double x, double y) {
        return new NetNode(id, x, y, type);
    }

    private static NetNode charger(String id, double x, double y) {
        return new NetNode(id, x, y, NodeType.CHARGER, 4);
    }

    /** 测试用双向双车道弧：允许对向同时通行，避免小图因单车道会车必然死锁。 */
    private static NetEdge edge(String id, String from, String to, double length) {
        return new NetEdge(id, from, to, length, EdgeDirection.TWO_WAY, 1.0, 1,
                com.agv.algorithm.model.LaneMode.DUAL);
    }

    /** CH1 — W1 — W2 — CH2 双向走廊。 */
    private RoadNet corridor() {
        return RoadNet.of(
                List.of(charger("CH1", 0, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        node("W2", NodeType.WORKSTATION, 20, 0),
                        charger("CH2", 30, 0)),
                List.of(edge("E1", "CH1", "W1", 10),
                        edge("E2", "W1", "W2", 10),
                        edge("E3", "W2", "CH2", 10)));
    }

    /** 双列车间：两条纵向巷道，可以绕行。 */
    private RoadNet twoLaneWorkshop() {
        List<NetNode> nodes = new ArrayList<>(List.of(
                charger("CH1", 0, 0),
                charger("CH2", 20, 0),
                node("J1", NodeType.JUNCTION, 0, 10),
                node("J2", NodeType.JUNCTION, 20, 10),
                node("J3", NodeType.JUNCTION, 0, 20),
                node("J4", NodeType.JUNCTION, 20, 20),
                node("W1", NodeType.WORKSTATION, 0, 30),
                node("W2", NodeType.WORKSTATION, 20, 30),
                node("B1", NodeType.BUFFER, 10, 10),
                node("B2", NodeType.BUFFER, 10, 20)
        ));
        List<NetEdge> edges = List.of(
                edge("E1", "CH1", "J1", 10),
                edge("E2", "J1", "J3", 10),
                edge("E3", "J3", "W1", 10),
                edge("E4", "CH2", "J2", 10),
                edge("E5", "J2", "J4", 10),
                edge("E6", "J4", "W2", 10),
                edge("E7", "J1", "B1", 10),
                edge("E8", "B1", "J2", 10),
                edge("E9", "J3", "B2", 10),
                edge("E10", "B2", "J4", 10),
                edge("E11", "B1", "B2", 10)
        );
        return RoadNet.of(nodes, edges);
    }

    @Test
    @DisplayName("单 AGV 跑完两个任务：完成数、里程、makespan 均大于 0")
    void singleAgvCompletesTasks() {
        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
                SimConfig.defaults(1, 42L, "nearest"));

        SimulationResult result = engine.runToCompletion();

        assertTrue(result.finished(), "仿真应正常结束");
        assertEquals(2, result.completedTasks());
        assertTrue(result.makespan() > 0);
        assertTrue(result.totalPathLen() > 0);
    }

    @Test
    @DisplayName("两车相向：边级预约生效，产生冲突等待但都能完成")
    void headOnConflictIsResolved() {
        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
                SimConfig.defaults(2, 7L, "nearest"));

        SimulationResult result = engine.runToCompletion();

        assertTrue(result.finished(), "相向冲突不应导致死锁");
        assertEquals(2, result.completedTasks());
        assertTrue(result.conflictCount() > 0, "应有冲突等待事件");
        assertTrue(result.totalWaitTick() > 0, "应有等待 tick 累计");
    }

    @Test
    @DisplayName("同一 seed 两次运行结果完全一致")
    void deterministicWithSameSeed() {
        List<Task> tasks = List.of(
                new Task(1, "W1", "W2"), new Task(2, "W2", "W1"),
                new Task(3, "W1", "W2"), new Task(4, "W2", "W1"));

        SimulationResult first = new SimulationEngine(twoLaneWorkshop(), tasks,
                SimConfig.defaults(3, 20261107L, "greedy-eta")).runToCompletion();
        SimulationResult second = new SimulationEngine(twoLaneWorkshop(), tasks,
                SimConfig.defaults(3, 20261107L, "greedy-eta")).runToCompletion();

        assertEquals(first.makespan(), second.makespan());
        assertEquals(first.totalPathLen(), second.totalPathLen(), 1e-9);
        assertEquals(first.totalWaitTick(), second.totalWaitTick());
        assertEquals(first.conflictCount(), second.conflictCount());
    }

    @Test
    @DisplayName("两种调度算法在 6 任务下都能跑完")
    void bothAlgorithmsCompleteTasks() {
        List<Task> tasks = List.of(
                new Task(1, "W1", "W2"), new Task(2, "W2", "W1"),
                new Task(3, "W1", "W2"), new Task(4, "W2", "W1"),
                new Task(5, "W1", "W1"), new Task(6, "W2", "W2"));

        SimulationResult nearest = new SimulationEngine(twoLaneWorkshop(), tasks,
                SimConfig.defaults(3, 99L, "nearest")).runToCompletion();
        SimulationResult greedy = new SimulationEngine(twoLaneWorkshop(), tasks,
                SimConfig.defaults(3, 99L, "greedy-eta")).runToCompletion();

        assertTrue(nearest.finished(), "就近分配应跑完");
        assertTrue(greedy.finished(), "贪心 ETA 应跑完");
        assertEquals(6, nearest.completedTasks());
        assertEquals(6, greedy.completedTasks());
    }

    @Test
    @DisplayName("标准场景四项指标稳定：补事件与诊断不得改变仿真行为")
    void standardScenarioMetricsStayStable() {
        List<Task> tasks = List.of(
                new Task(1, "W1", "W2"), new Task(2, "W2", "W1"),
                new Task(3, "W1", "W2"), new Task(4, "W2", "W1"),
                new Task(5, "W1", "W1"), new Task(6, "W2", "W2"));

        SimulationResult result = new SimulationEngine(twoLaneWorkshop(), tasks,
                SimConfig.defaults(3, 99L, "greedy-eta")).runToCompletion();

        assertEquals(1133L, result.makespan());
        assertEquals(340.0, result.totalPathLen(), 1e-9);
        assertEquals(124L, result.totalWaitTick());
        assertEquals(4, result.conflictCount());
    }

    private static List<SimEvent> runToEnd(SimulationEngine engine) {
        List<SimEvent> events = new ArrayList<>();
        while (!engine.finished() && !engine.deadlocked() && engine.tick() < 20_000) {
            events.addAll(engine.step().events());
        }
        return events;
    }

    private static List<SimFrame> runFrames(SimulationEngine engine) {
        List<SimFrame> frames = new ArrayList<>();
        while (!engine.finished() && !engine.deadlocked() && engine.tick() < 20_000) {
            frames.add(engine.step());
        }
        return frames;
    }

    private static SimEvent lastEvent(List<SimEvent> events, SimEvent.Type type) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).type() == type) {
                return events.get(i);
            }
        }
        return null;
    }

    /** CH1 — W1 — W2：只有一个充电位，用于触发回充宽限期。 */
    private RoadNet singleChargerLine() {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        node("W2", NodeType.WORKSTATION, 20, 0)),
                List.of(edge("E1", "CH1", "W1", 10),
                        edge("E2", "W1", "W2", 10)));
    }

    /** 单车道走廊：用于触发真实的倒车让行。 */
    private RoadNet singleLaneCorridor() {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        node("W2", NodeType.WORKSTATION, 20, 0),
                        node("CH2", NodeType.CHARGER, 30, 0)),
                List.of(singleEdge("E1", "CH1", "W1", 10),
                        singleEdge("E2", "W1", "W2", 10),
                        singleEdge("E3", "W2", "CH2", 10)));
    }

    private static NetEdge singleEdge(String id, String from, String to, double length) {
        return new NetEdge(id, from, to, length, EdgeDirection.TWO_WAY, 1.0, 1,
                com.agv.algorithm.model.LaneMode.SINGLE);
    }

    /** 一个容量 4 的充电位，3 台车初始同点。 */
    private RoadNet coLocationLine() {
        return RoadNet.of(
                List.of(new NetNode("CH1", 0, 0, NodeType.CHARGER, 4),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        node("W2", NodeType.WORKSTATION, 20, 0)),
                List.of(edge("E1", "CH1", "W1", 10),
                        edge("E2", "W1", "W2", 10)));
    }

    /** 两个入口汇到同一个单容量工位，用于触发节点冲突。 */
    private RoadNet sharedGoalNet() {
        return RoadNet.of(
                List.of(charger("CH1", 0, 0),
                        charger("CH2", 0, 20),
                        node("J1", NodeType.JUNCTION, 10, 10),
                        node("W1", NodeType.WORKSTATION, 20, 10)),
                List.of(edge("E1", "CH1", "J1", 10),
                        edge("E2", "CH2", "J1", 10),
                        edge("E3", "J1", "W1", 10)));
    }

    @Test
    @DisplayName("冲突等待结束后发出 WAIT_RESOLVED，并更新最长等待")
    void conflictWaitIsClosedByResolvedEvent() {
        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
                SimConfig.defaults(2, 7L, "nearest"));

        List<SimEvent> events = runToEnd(engine);

        assertTrue(events.stream().anyMatch(e -> e.type() == SimEvent.Type.WAIT_CONFLICT),
                "相向冲突应至少等待一次");
        List<SimEvent> resolved = events.stream()
                .filter(e -> e.type() == SimEvent.Type.WAIT_RESOLVED)
                .toList();
        assertFalse(resolved.isEmpty(), "等待结束后应发出 WAIT_RESOLVED");
        assertTrue(resolved.stream().allMatch(e -> e.detail().startsWith("等待 ")),
                "WAIT_RESOLVED 的 detail 应写明等待 tick");
        assertTrue(engine.diagnostics().maxBlockedTicks() > 0, "最长阻塞段应被记录");
    }

    @Test
    @DisplayName("卸货瞬时完成：UNLOADED 与 TASK_DONE 同 tick 且在前")
    void unloadedEventPrecedesTaskDone() {
        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
                SimConfig.defaults(1, 1L, "nearest"));

        List<SimEvent> events = runToEnd(engine);
        List<Integer> unloadedIndexes = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).type() == SimEvent.Type.UNLOADED) {
                unloadedIndexes.add(i);
            }
        }

        List<SimEvent> done = events.stream()
                .filter(e -> e.type() == SimEvent.Type.TASK_DONE)
                .toList();
        assertEquals(2, done.size());
        for (SimEvent taskDone : done) {
            boolean paired = unloadedIndexes.stream()
                    .map(events::get)
                    .anyMatch(u -> u.tick() == taskDone.tick() && u.agvId().equals(taskDone.agvId()));
            assertTrue(paired, "每个 TASK_DONE 都应有同 tick 的 UNLOADED：" + taskDone);
        }
        SimEvent firstDone = done.get(0);
        int doneIndex = events.indexOf(firstDone);
        int unloadedIndex = events.indexOf(events.stream()
                .filter(e -> e.type() == SimEvent.Type.UNLOADED)
                .filter(e -> e.tick() == firstDone.tick() && e.agvId().equals(firstDone.agvId()))
                .findFirst().orElseThrow());
        assertTrue(unloadedIndex < doneIndex, "UNLOADED 应先于 TASK_DONE");
    }

    @Test
    @DisplayName("结束事件写明归位台数")
    void finishEventReportsParkedCount() {
        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2")),
                SimConfig.defaults(1, 1L, "nearest"));

        List<SimEvent> events = runToEnd(engine);
        SimEvent finished = lastEvent(events, SimEvent.Type.FINISHED);

        assertTrue(engine.finished());
        assertFalse(finished == null);
        assertTrue(finished.detail().contains("归位 1/1 台"), finished.detail());
        assertFalse(engine.diagnostics().graceFinished());
        assertEquals(0, engine.diagnostics().unparkedAtFinish());
    }

    @Test
    @DisplayName("停不进充电位时按宽限期结束，并记录未归位车辆数")
    void graceFinishReportsUnparkedAgvs() {
        SimulationEngine engine = new SimulationEngine(singleChargerLine(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W1", "W2")),
                SimConfig.defaults(2, 5L, "nearest"));

        List<SimEvent> events = runToEnd(engine);
        SimDiagnostics diagnostics = engine.diagnostics();
        SimEvent finished = lastEvent(events, SimEvent.Type.FINISHED);

        assertTrue(engine.finished(), "回充宽限期必须保证仿真结束");
        assertTrue(diagnostics.graceFinished(), "只有一个充电位时第二台车应停不进去");
        assertEquals(1, diagnostics.unparkedAtFinish());
        assertTrue(finished.detail().contains("未归位"), finished.detail());
    }

    @Test
    @DisplayName("任意 tick、任意节点，占位车辆数不超过容量")
    void nodeCapacityIsRespected() {
        List<Task> tasks = List.of(
                new Task(1, "W1", "W2"), new Task(2, "W2", "W1"),
                new Task(3, "W1", "W2"), new Task(4, "W2", "W1"),
                new Task(5, "W1", "W2"), new Task(6, "W2", "W1"));

        for (RoadNet net : List.of(corridor(), twoLaneWorkshop())) {
            for (int agvCount : new int[]{2, 3, 4}) {
                for (String algorithm : List.of("nearest", "greedy-eta")) {
                    SimulationEngine engine = new SimulationEngine(net, tasks,
                            SimConfig.defaults(agvCount, 20261107L, algorithm));
                    while (!engine.finished() && !engine.deadlocked() && engine.tick() < 20_000) {
                        SimFrame frame = engine.step();
                        Map<String, Integer> occupied = new HashMap<>();
                        for (AgvSnapshot snapshot : frame.agvs()) {
                            if (snapshot.arcId() == null && snapshot.nodeId() != null) {
                                occupied.merge(snapshot.nodeId(), 1, Integer::sum);
                            }
                        }
                        for (Map.Entry<String, Integer> entry : occupied.entrySet()) {
                            NetNode node = net.node(entry.getKey());
                            int capacity = node == null ? 1 : Math.max(1, node.capacity());
                            assertTrue(entry.getValue() <= capacity,
                                    "tick " + frame.tick() + " 节点 " + entry.getKey()
                                            + " 有 " + entry.getValue() + " 台车，容量 " + capacity);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("无法解开的等待环标记为死锁，而不是互相穿透")
    void deadlockIsReported() {
        RoadNet line = RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("CH2", NodeType.CHARGER, 10, 0)),
                List.of(edge("E1", "CH1", "CH2", 10)));

        SimulationEngine engine = new SimulationEngine(line,
                List.of(new Task(1, "CH1", "CH2"), new Task(2, "CH2", "CH1")),
                SimConfig.defaults(2, 7L, "nearest"));
        List<SimFrame> frames = runFrames(engine);

        assertTrue(engine.deadlocked(), "两车互相等待对方占用的终点，应判定为死锁");
        assertFalse(engine.finished(), "死锁时不应报告全部任务完成");
        assertTrue(frames.stream()
                        .flatMap(frame -> frame.events().stream())
                        .anyMatch(event -> event.type() == SimEvent.Type.DEADLOCK),
                "死锁应产生 DEADLOCK 事件");
        SimEvent deadlock = frames.stream()
                .flatMap(frame -> frame.events().stream())
                .filter(event -> event.type() == SimEvent.Type.DEADLOCK)
                .findFirst().orElseThrow();
        assertTrue(deadlock.detail().contains("2 台车互等"), deadlock.detail());
        assertTrue(deadlock.detail().contains("想进"), deadlock.detail());
        assertTrue(deadlock.detail().contains("被 AGV-2 挡住"), deadlock.detail());
        assertTrue(deadlock.detail().contains("被 AGV-1 挡住"), deadlock.detail());
        assertTrue(deadlock.detail().contains("（充电位）"), deadlock.detail());
        assertFalse(deadlock.detail().contains("@"), deadlock.detail());
        assertFalse(deadlock.detail().contains("→"), deadlock.detail());
        assertTrue(deadlock.detail().endsWith(engine.deadlockDetail()), deadlock.detail());
        assertEquals("2 台车互等：AGV-1 想进 CH2（充电位），被 AGV-2 挡住；AGV-2 想进 CH1（充电位），被 AGV-1 挡住",
                engine.deadlockDetail());

        SimulationEngine rerun = new SimulationEngine(line,
                List.of(new Task(1, "CH1", "CH2"), new Task(2, "CH2", "CH1")),
                SimConfig.defaults(2, 7L, "nearest"));
        rerun.runToCompletion();
        assertEquals(engine.deadlockDetail(), rerun.deadlockDetail(), "同种子两次运行应给出逐字一致的死锁文案");
    }

    @Test
    @DisplayName("倒车抵达起点后进入 30 tick 冷却")
    void retreatStartsCooldown() {
        SimulationEngine engine = new SimulationEngine(singleLaneCorridor(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
                SimConfig.defaults(2, 7L, "nearest"));

        boolean cooling = false;
        while (!engine.finished() && !engine.deadlocked() && engine.tick() < 1_000) {
            engine.step();
            if (engine.yieldCooldown("AGV-1") > 0 || engine.yieldCooldown("AGV-2") > 0) {
                cooling = true;
                break;
            }
        }
        assertTrue(cooling, "倒车让行后应进入冷却，避免立刻重新抢同一资源");
    }

    @Test
    @DisplayName("同节点多车记录开始、变化和结束，不逐 tick 刷屏")
    void coLocationIsAggregated() {
        SimulationEngine engine = new SimulationEngine(coLocationLine(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W1", "W2"), new Task(3, "W1", "W2")),
                SimConfig.defaults(3, 1L, "nearest"));

        List<SimEvent> events = new ArrayList<>(engine.step().events());
        List<SimEvent> starts = events.stream()
                .filter(event -> event.type() == SimEvent.Type.CO_LOCATION)
                .filter(event -> event.detail().contains("AGV-1") && event.detail().contains("AGV-2")
                        && event.detail().contains("AGV-3"))
                .toList();
        assertFalse(starts.isEmpty(), "初始 3 台车都在 CH1，应记录一条位置重合");
        assertTrue(starts.get(0).detail().contains("CH1"), starts.get(0).detail());

        while (!engine.finished() && !engine.deadlocked() && engine.tick() < 1_000
                && events.stream().noneMatch(event -> event.type() == SimEvent.Type.CO_LOCATION_END)) {
            events.addAll(engine.step().events());
        }
        SimEvent end = events.stream()
                .filter(event -> event.type() == SimEvent.Type.CO_LOCATION_END)
                .findFirst().orElse(null);
        assertFalse(end == null, "车辆离开后应记录重合结束");
        assertTrue(end.detail().contains("持续"), end.detail());
        assertTrue(engine.diagnostics().coLocationEvents() >= 2, "至少一条开始和一条结束");
    }

    @Test
    @DisplayName("等待事件的容量冲突写出占用者")
    void waitConflictNamesHolder() {
        SimulationEngine engine = new SimulationEngine(sharedGoalNet(),
                List.of(new Task(1, "CH1", "W1"), new Task(2, "CH2", "W1")),
                SimConfig.defaults(2, 1L, "nearest"));

        List<SimEvent> events = runToEnd(engine);
        SimEvent conflict = events.stream()
                .filter(event -> event.type() == SimEvent.Type.WAIT_CONFLICT)
                .filter(event -> event.detail().contains("占用者 AGV-"))
                .findFirst().orElse(null);
        assertFalse(conflict == null, "容量冲突应写出占用者");
        assertTrue(conflict.detail().contains("追尾冲突"), conflict.detail());
    }

    @Test
    @DisplayName("卸货占用 unloadTicks，UNLOADED 与 TASK_DONE 同 tick")
    void unloadTakesConfiguredTicks() {
        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2")),
                new SimConfig(1, 1L, "nearest", 5, 7, 30, 200_000));

        List<SimEvent> events = runToEnd(engine);
        SimEvent unloading = events.stream()
                .filter(event -> event.type() == SimEvent.Type.UNLOADING)
                .findFirst().orElse(null);
        SimEvent unloaded = events.stream()
                .filter(event -> event.type() == SimEvent.Type.UNLOADED)
                .findFirst().orElse(null);
        SimEvent done = events.stream()
                .filter(event -> event.type() == SimEvent.Type.TASK_DONE)
                .findFirst().orElse(null);

        assertFalse(unloading == null, "到达卸货点应发出 UNLOADING");
        assertFalse(unloaded == null, "卸货结束应发出 UNLOADED");
        assertFalse(done == null, "卸货结束应完成对应任务");
        assertEquals(7L, unloaded.tick() - unloading.tick(), "卸货应占用 unloadTicks");
        assertEquals(unloaded.tick(), done.tick(), "UNLOADED 与 TASK_DONE 应同 tick");
        assertTrue(done.tick() > unloading.tick(), "任务不应在开始卸货时立即完成");
    }

    @Test
    @DisplayName("最长阻塞段跨重规划累计，不会被 30 tick 清零")
    void maxBlockedTicksSurvivesReplans() {
        SimulationEngine engine = new SimulationEngine(singleChargerLine(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W1", "W2")),
                SimConfig.defaults(2, 5L, "nearest"));

        runToEnd(engine);

        assertTrue(engine.diagnostics().maxBlockedTicks() > 300,
                "停不进充电位的等待应累计为完整阻塞段，而不是被重规划清零");
    }

    @Test
    @DisplayName("帧数据包含插值坐标与状态")
    void frameContainsSnapshots() {
        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2")),
                SimConfig.defaults(1, 1L, "nearest"));

        engine.step();
        engine.step();
        SimFrame frame = engine.step();

        assertEquals(1, frame.agvs().size());
        AgvSnapshot snapshot = frame.agvs().get(0);
        assertTrue(snapshot.x() >= 0);
        assertTrue(snapshot.taskId() >= -1);
    }

    @Test
    @DisplayName("任务不可达：构造期直接拒绝")
    void unreachableTaskRejected() {
        RoadNet broken = RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        node("W9", NodeType.WORKSTATION, 99, 99)),
                List.of(edge("E1", "CH1", "W1", 10)));

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(broken, List.of(new Task(1, "W1", "W9")),
                        SimConfig.defaults(1, 1L, "nearest")));
    }

    @Test
    @DisplayName("没有充电位：构造期直接拒绝")
    void missingChargerRejected() {
        RoadNet noCharger = RoadNet.of(
                List.of(node("W1", NodeType.WORKSTATION, 0, 0),
                        node("W2", NodeType.WORKSTATION, 10, 0)),
                List.of(edge("E1", "W1", "W2", 10)));

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(noCharger, List.of(new Task(1, "W1", "W2")),
                        SimConfig.defaults(1, 1L, "nearest")));
    }

    @Test
    @DisplayName("未知算法码：构造期直接拒绝")
    void unknownAlgorithmRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(corridor(), List.of(new Task(1, "W1", "W2")),
                        SimConfig.defaults(1, 1L, "no-such-algorithm")));
    }

    @Test
    @DisplayName("预约表：区间重叠与容量判定")
    void reservationTableHonoursIntervals() {
        ReservationTable table = new ReservationTable();
        table.reserve("AGV-1", "E1+", 10, 20);

        assertFalse(table.canReserve("E1+", 15, 25, 1), "重叠区间应判满");
        assertTrue(table.canReserve("E1+", 20, 30, 1), "紧邻不重叠应可预约");
        assertTrue(table.canReserve("E1+", 15, 25, 2), "容量 2 时应可再容纳一台");

        table.release("E1+", "AGV-1");
        assertTrue(table.canReserve("E1+", 15, 25, 1));
    }
}
