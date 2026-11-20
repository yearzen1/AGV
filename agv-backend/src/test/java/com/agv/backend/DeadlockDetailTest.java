package com.agv.backend;

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
import com.agv.backend.support.RoadNetTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlockDetailTest {

    @Test
    @DisplayName("直线车间 8 台死锁时给出互等环，而不是只列等待车")
    void lineWorkshopReportsWaitCycle() throws Exception {
        SimulationEngine engine = new SimulationEngine(readNet(RoadNetTemplates.LINE_WORKSHOP),
                tasks(), SimConfig.defaults(8, 20261107L, "nearest"));

        SimulationResult result = engine.runToCompletion();

        assertTrue(result.deadlocked(), "单车道 8 台应进入死锁");
        String detail = engine.deadlockDetail();
        assertTrue(detail.contains("等待环"), detail);
        assertTrue(detail.contains("AGV-"), detail);
        assertTrue(detail.contains("→"), detail);
    }

    private static List<Task> tasks() {
        String[] stations = {"W1", "W2", "W3"};
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String from = stations[i % 3];
            String to = stations[(i + 1 + i / 3) % 3];
            if (from.equals(to)) {
                to = stations[(i + 2) % 3];
            }
            tasks.add(new Task(i + 1, from, to));
        }
        return tasks;
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
