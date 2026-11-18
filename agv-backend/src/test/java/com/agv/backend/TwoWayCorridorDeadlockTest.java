package com.agv.backend;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.model.RoadNetCapacity;
import com.agv.algorithm.model.RoadNetValidator;
import com.agv.algorithm.model.ValidationResult;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.algorithm.sim.Task;
import com.agv.backend.support.DemoScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定理必要性：同样的任务，双向走廊上两台车就能互等卡死；换成单向环就能跑完。
 */
class TwoWayCorridorDeadlockTest {

    private static RoadNet twoWayCorridor() {
        return RoadNet.of(
                List.of(new NetNode("CH1", 0, 0, NodeType.CHARGER, 2),
                        new NetNode("X", 10, 0, NodeType.WORKSTATION, 1),
                        new NetNode("Y", 20, 0, NodeType.WORKSTATION, 1),
                        new NetNode("CH2", 30, 0, NodeType.CHARGER, 2)),
                List.of(new NetEdge("E1", "CH1", "X", 10, EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E2", "X", "Y", 10, EdgeDirection.TWO_WAY, 1.0, 1),
                        new NetEdge("E3", "Y", "CH2", 10, EdgeDirection.TWO_WAY, 1.0, 1)));
    }

    @Test
    @DisplayName("双向走廊：X→Y 与 Y→X 两台车互相等对方的节点，跑不完")
    void twoWayCorridorStallsWithTwoCars() {
        SimulationResult result = new SimulationEngine(twoWayCorridor(),
                List.of(new Task(1, "X", "Y"), new Task(2, "Y", "X")),
                SimConfig.defaults(2, 1L, "nearest")).runToCompletion();

        assertTrue(result.completedTasks() < 2,
                "两台车互相等对方的节点，不可能全部完成，实际完成 " + result.completedTasks());
    }

    @Test
    @DisplayName("同一任务换到单向环：同样两台车能跑完")
    void oneWayRingFinishesWithSameTasks() throws Exception {
        SimulationResult result = new SimulationEngine(DemoScenario.standardNet(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")),
                SimConfig.defaults(2, 1L, "nearest")).runToCompletion();

        assertTrue(result.finished(), "单向环应跑完");
        assertEquals(2, result.completedTasks());
    }

    @Test
    @DisplayName("双向走廊被校验器拦下，车队上限也只剩 1 台")
    void twoWayCorridorIsRejected() {
        RoadNet net = twoWayCorridor();
        ValidationResult result = RoadNetValidator.validate(net.nodes().values().stream().toList(),
                net.edges().values().stream().toList());

        assertFalse(result.valid(), "双向走廊不应通过校验");
        assertTrue(result.errors().stream()
                        .anyMatch(e -> RoadNetValidator.TWO_WAY_EDGE.equals(e.code())),
                () -> "应报双向路段，实际：" + result.errors());
        assertEquals(1, RoadNetCapacity.maxAgvCount(net), "X—Y 二环容量只有 2，拓扑上限 1 台");
    }
}
