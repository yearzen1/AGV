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

    private static RoadNet line(LaneMode laneMode) {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("J1", NodeType.JUNCTION, 10, 0),
                        node("J2", NodeType.JUNCTION, 20, 0),
                        node("CH2", NodeType.CHARGER, 30, 0),
                        node("W1", NodeType.WORKSTATION, 10, 10),
                        node("W2", NodeType.WORKSTATION, 20, 10)),
                List.of(new NetEdge("E1", "CH1", "J1", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1, laneMode),
                        new NetEdge("E2", "J1", "J2", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1, laneMode),
                        new NetEdge("E3", "J2", "CH2", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1, laneMode),
                        new NetEdge("E4", "J1", "W1", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1, laneMode),
                        new NetEdge("E5", "J2", "W2", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1, laneMode)));
    }

    private static RoadNet crossing() {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("CH2", NodeType.CHARGER, 0, 20),
                        node("J1", NodeType.JUNCTION, 10, 10),
                        node("W1", NodeType.WORKSTATION, 20, 0),
                        node("W2", NodeType.WORKSTATION, 20, 20)),
                List.of(new NetEdge("E1", "CH1", "J1", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E2", "J1", "W1", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E3", "CH2", "J1", 10,
                                EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E4", "J1", "W2", 10,
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
        while (!engine.finished() && !engine.deadlocked() && engine.tick() < 20000) {
            frames.add(engine.step());
        }

        assertTrue(engine.finished() || engine.deadlocked(), "单车道双向应结束或标记死锁");
        assertTrue(!sawOppositeOnEdge(frames, "E2"), "单车道同一时刻只能有一个方向");
    }

    @Test
    @DisplayName("双车道双向：对向车可以同时各占一条车道")
    void dualLaneAllowsOppositeDirections() {
        SimulationEngine engine = new SimulationEngine(line(LaneMode.DUAL),
                List.of(new Task(1, "CH1", "W2"), new Task(2, "CH2", "W1")),
                SimConfig.defaults(2, 20261107L, "nearest"));

        List<SimFrame> frames = new ArrayList<>();
        while (!engine.finished() && !engine.deadlocked() && engine.tick() < 20000) {
            frames.add(engine.step());
        }

        assertTrue(engine.finished() || engine.deadlocked(), "双车道双向应结束或标记死锁");
        assertTrue(sawOppositeOnEdge(frames, "E2"), "双车道应出现正反两向同时通行");
    }

    @Test
    @DisplayName("路口同一 tick 最多一辆车占据")
    void junctionIsCriticalSection() {
        SimulationEngine engine = new SimulationEngine(crossing(),
                List.of(new Task(1, "CH1", "W2"), new Task(2, "CH2", "W1")),
                SimConfig.defaults(2, 20261107L, "nearest"));

        int maxAtJunction = 0;
        while (!engine.finished() && !engine.deadlocked() && engine.tick() < 20000) {
            SimFrame frame = engine.step();
            int atJunction = (int) frame.agvs().stream()
                    .filter(a -> a.arcId() == null && "J1".equals(a.nodeId()))
                    .count();
            maxAtJunction = Math.max(maxAtJunction, atJunction);
        }

        assertTrue(engine.finished() || engine.deadlocked(), "路口互斥应结束或标记死锁");
        assertTrue(maxAtJunction <= 1, "路口同一时刻最多一辆车");
    }
}
