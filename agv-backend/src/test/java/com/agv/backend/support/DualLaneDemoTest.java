package com.agv.backend.support;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.LaneMode;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.algorithm.sim.Task;
import com.agv.backend.dto.TaskDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualLaneDemoTest {

    @Test
    @DisplayName("会车任务集：12 单在 W1 与 W3 之间交替往返")
    void demoTaskSetAlternatesOppositeFlows() {
        List<TaskDto> tasks = DataInitializer.dualLaneDemoTasks();

        assertEquals(12, tasks.size());
        for (int i = 0; i < tasks.size(); i++) {
            TaskDto task = tasks.get(i);
            if (i % 2 == 0) {
                assertEquals("W1", task.from());
                assertEquals("W3", task.to());
            } else {
                assertEquals("W3", task.from());
                assertEquals("W1", task.to());
            }
        }
    }

    @Test
    @DisplayName("同一会车任务集下，双车道与单车道的指标必须不同")
    void dualLaneChangesMetrics() throws Exception {
        SimulationResult single = run(RoadNetTemplates.STANDARD_WORKSHOP);
        SimulationResult dual = run(RoadNetTemplates.DUAL_LANE_WORKSHOP);

        assertTrue(single.finished(), "单车道应能跑完会车任务集");
        assertTrue(dual.finished(), "双车道应能跑完会车任务集");
        boolean different = single.makespan() != dual.makespan()
                || Double.compare(single.totalPathLen(), dual.totalPathLen()) != 0
                || single.totalWaitTick() != dual.totalWaitTick()
                || single.conflictCount() != dual.conflictCount();
        assertTrue(different, "双车道应改变会车结果，而不是与单车道完全相同");
        assertNotEquals(0, dual.completedTasks());
    }

    private SimulationResult run(String template) throws Exception {
        List<Task> tasks = DataInitializer.dualLaneDemoTasks().stream()
                .map(dto -> new Task(dto.id(), dto.from(), dto.to()))
                .toList();
        SimulationEngine engine = new SimulationEngine(readNet(template), tasks,
                SimConfig.defaults(6, 20261107L, "nearest"));
        return engine.runToCompletion();
    }

    private RoadNet readNet(String json) throws Exception {
        JsonNode root = new ObjectMapper().readTree(json);
        List<NetNode> nodes = new ArrayList<>();
        for (JsonNode node : root.get("nodes")) {
            nodes.add(new NetNode(node.get("id").asText(), node.get("x").asDouble(),
                    node.get("y").asDouble(), NodeType.valueOf(node.get("type").asText()),
                    node.has("capacity") ? node.get("capacity").asInt() : 1));
        }
        List<NetEdge> edges = new ArrayList<>();
        for (JsonNode edge : root.get("edges")) {
            String laneMode = edge.has("laneMode") ? edge.get("laneMode").asText() : null;
            edges.add(new NetEdge(edge.get("id").asText(), edge.get("from").asText(),
                    edge.get("to").asText(), edge.get("length").asDouble(),
                    EdgeDirection.valueOf(edge.get("direction").asText()),
                    edge.get("speed").asDouble(), edge.get("capacity").asInt(),
                    "DUAL".equalsIgnoreCase(laneMode) ? LaneMode.DUAL : LaneMode.SINGLE));
        }
        return RoadNet.of(nodes, edges);
    }
}
