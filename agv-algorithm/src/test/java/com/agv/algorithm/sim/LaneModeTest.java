package com.agv.algorithm.sim;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.LaneMode;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneModeTest {

    private static NetNode node(String id, NodeType type, double x, double y) {
        return new NetNode(id, x, y, type);
    }

    /**
     * CH1 — W1 — A1 — A2 — W2 — CH2，A1—A2 是用于观察对向的中间路段。
     * 任务终点在远端工位，两台车进入中间路段时对方的下一节点仍然空闲。
     */
    private static RoadNet line(LaneMode laneMode) {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        new NetNode("A1", 20, 0, NodeType.JUNCTION, 2),
                        new NetNode("A2", 50, 0, NodeType.JUNCTION, 2),
                        node("W2", NodeType.WORKSTATION, 60, 0),
                        node("CH2", NodeType.CHARGER, 70, 0)),
                List.of(new NetEdge("E1", "CH1", "W1", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E2", "W1", "A1", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E3", "A1", "A2", 30,
                                EdgeDirection.TWO_WAY, 1.0, 1, laneMode),
                        new NetEdge("E4", "A2", "W2", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E5", "W2", "CH2", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1)));
    }

    private static RoadNet crossing() {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 10),
                        node("W1", NodeType.WORKSTATION, 5, 10),
                        node("J1", NodeType.JUNCTION, 10, 10),
                        node("W2", NodeType.WORKSTATION, 15, 10),
                        node("CH2", NodeType.CHARGER, 20, 10)),
                List.of(new NetEdge("E1", "CH1", "W1", 5,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E2", "W1", "J1", 5,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E3", "J1", "W2", 5,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E4", "W2", "CH2", 5,
                                EdgeDirection.TWO_WAY, 1.0, 1)));
    }

    private static boolean sawOppositeOnEdge(List<SimFrame> frames, String edgePrefix) {
        for (SimFrame frame : frames) {
            boolean forward = frame.agvs().stream()
                    .anyMatch(a -> a.arcId() != null && a.arcId().equals(edgePrefix + "+"));
            boolean reverse = frame.agvs().stream()
                    .anyMatch(a -> a.arcId() != null && a.arcId().equals(edgePrefix + "-"));
            if (forward && reverse) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("单车道双向：对向车不能同时出现在同一条边")
    void singleLaneSerializesOppositeDirections() {
        SimulationEngine engine = new SimulationEngine(line(LaneMode.SINGLE),
                List.of(new Task(1, "CH1", "W2"), new Task(2, "CH2", "W1")),
                SimConfig.defaults(2, 20261107L, "nearest"));

        List<SimFrame> frames = new ArrayList<>();
        while (!engine.finished() && engine.tick() < 10000) {
            frames.add(engine.step());
        }

        assertTrue(engine.finished(), "单车道双向不应死锁");
        assertTrue(!sawOppositeOnEdge(frames, "E3"), "单车道同一时刻只能有一个方向");
    }

    @Test
    @DisplayName("双车道双向：对向车可以同时各占一条车道")
    void dualLaneAllowsOppositeDirections() {
        SimulationEngine engine = new SimulationEngine(line(LaneMode.DUAL),
                List.of(new Task(1, "CH1", "W2"), new Task(2, "CH2", "W1")),
                SimConfig.defaults(2, 20261107L, "nearest"));

        List<SimFrame> frames = new ArrayList<>();
        while (!engine.finished() && engine.tick() < 10000) {
            frames.add(engine.step());
        }

        assertTrue(engine.finished(), "双车道双向应能完成");
        assertTrue(sawOppositeOnEdge(frames, "E3"), "双车道应出现正反两向同时通行");
    }

    @Test
    @DisplayName("路口同一 tick 最多一辆车占据")
    void junctionIsCriticalSection() {
        SimulationEngine engine = new SimulationEngine(crossing(),
                List.of(new Task(1, "CH1", "W2"), new Task(2, "CH2", "W1")),
                SimConfig.defaults(2, 20261107L, "nearest"));

        int maxAtJunction = 0;
        while (!engine.finished() && engine.tick() < 10000) {
            SimFrame frame = engine.step();
            int atJunction = (int) frame.agvs().stream()
                    .filter(a -> "J1".equals(a.nodeId()))
                    .count();
            maxAtJunction = Math.max(maxAtJunction, atJunction);
        }

        assertTrue(engine.finished(), "路口互斥不应导致死锁");
        assertEquals(1, maxAtJunction, "路口同一时刻最多一辆车");
    }
}
