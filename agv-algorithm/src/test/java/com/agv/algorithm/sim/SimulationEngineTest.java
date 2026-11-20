package com.agv.algorithm.sim;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineTest {

    private static NetNode node(String id, NodeType type, double x, double y) {
        return new NetNode(id, x, y, type);
    }

    private static NetEdge edge(String id, String from, String to, double length) {
        return new NetEdge(id, from, to, length, EdgeDirection.TWO_WAY, 1.0, 1);
    }

    /** CH1 — W1 — W2 — CH2 双向走廊，单车道。 */
    private RoadNet corridor() {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        node("W2", NodeType.WORKSTATION, 20, 0),
                        node("CH2", NodeType.CHARGER, 30, 0)),
                List.of(edge("E1", "CH1", "W1", 10),
                        edge("E2", "W1", "W2", 10),
                        edge("E3", "W2", "CH2", 10)));
    }

    /** 双列车间：两条纵向巷道，可以绕行。 */
    private RoadNet twoLaneWorkshop() {
        List<NetNode> nodes = new ArrayList<>(List.of(
                node("CH1", NodeType.CHARGER, 0, 0),
                node("CH2", NodeType.CHARGER, 20, 0),
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

        assertEquals(1335L, result.makespan());
        assertEquals(350.0, result.totalPathLen(), 1e-9);
        assertEquals(1552L, result.totalWaitTick());
        assertEquals(9, result.conflictCount());
    }

    private static List<SimEvent> runToEnd(SimulationEngine engine) {
        List<SimEvent> events = new ArrayList<>();
        while (!engine.finished() && engine.tick() < 20_000) {
            events.addAll(engine.step().events());
        }
        return events;
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
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
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
    @DisplayName("每次检测到等待环都落到倒车成功或逻辑让行")
    void deadlockBreakAlwaysEndsInRetreatOrLogicalYield() {
        SimulationEngine corridorRun = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1"),
                        new Task(3, "W1", "W2")),
                SimConfig.defaults(3, 7L, "nearest"));
        corridorRun.runToCompletion();
        assertDeadlockAccounting(corridorRun.diagnostics());

        SimulationEngine workshopRun = new SimulationEngine(twoLaneWorkshop(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1"),
                        new Task(3, "W1", "W2"), new Task(4, "W2", "W1")),
                SimConfig.defaults(4, 20261107L, "greedy-eta"));
        workshopRun.runToCompletion();
        assertDeadlockAccounting(workshopRun.diagnostics());
    }

    private static void assertDeadlockAccounting(SimDiagnostics diagnostics) {
        assertEquals(diagnostics.deadlockBreakCount(),
                diagnostics.retreatSuccessCount() + diagnostics.logicalYieldCount(),
                "检测到等待环后必须落到倒车成功或逻辑让行");
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
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
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
