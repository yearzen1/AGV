package com.agv.backend;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.algorithm.sim.Task;
import com.agv.backend.dto.RoadNetPayload;
import com.agv.backend.service.RoadNetService;
import com.agv.backend.support.RoadNetTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 规模压测：标准车间 + 20 单，覆盖 2/3/5/8 台车与两种算法。 */
class EngineScaleTest {

    @Test
    void runScaleMatrix() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RoadNetPayload payload = mapper.readValue(RoadNetTemplates.STANDARD_WORKSHOP, RoadNetPayload.class);
        RoadNet net = RoadNet.of(
                payload.nodes().stream()
                        .map(n -> new com.agv.algorithm.model.NetNode(n.id(), n.x(), n.y(),
                                com.agv.algorithm.model.NodeType.valueOf(n.type())))
                        .toList(),
                payload.edges().stream()
                        .map(e -> new com.agv.algorithm.model.NetEdge(e.id(), e.from(), e.to(), e.length(),
                                com.agv.algorithm.model.EdgeDirection.valueOf(e.direction()), e.speed(), e.capacity(),
                                "DUAL".equalsIgnoreCase(e.laneMode())
                                        ? com.agv.algorithm.model.LaneMode.DUAL
                                        : com.agv.algorithm.model.LaneMode.SINGLE))
                        .toList());

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

        List<String> lines = new ArrayList<>();
        for (int count : new int[]{2, 3, 5, 8}) {
            for (String algorithm : new String[]{"nearest", "greedy-eta"}) {
                SimulationResult result = new SimulationEngine(net, tasks,
                        SimConfig.defaults(count, 20261107L, algorithm)).runToCompletion();
                lines.add(String.format("%d\t%s\tdone=%s\tcompleted=%d/%d\tmakespan=%d\tpath=%.1f\twait=%d\tconflict=%d\tticks=%d",
                        count, algorithm, result.finished(), result.completedTasks(), tasks.size(), result.makespan(),
                        result.totalPathLen(), result.totalWaitTick(), result.conflictCount(), result.ticks()));
                org.junit.jupiter.api.Assertions.assertTrue(result.finished(),
                        count + " 台车 + " + algorithm + " 未能跑完");
                org.junit.jupiter.api.Assertions.assertEquals(20, result.completedTasks());
                org.junit.jupiter.api.Assertions.assertTrue(result.makespan() > 0);
            }
        }
        Files.write(Path.of("target", "scale-matrix.txt"), lines);
    }
}
