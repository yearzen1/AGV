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
