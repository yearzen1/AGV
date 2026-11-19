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
import com.agv.algorithm.sim.TaskOperation;
import com.agv.backend.dto.RoadNetPayload;
import com.agv.backend.support.RoadNetTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仓储车间规模压测：分区布局 + 20 单装/卸任务，覆盖 2/3/5/8 台车与两种算法。 */
class WarehouseScaleTest {

    @Test
    void runWarehouseScaleMatrix() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RoadNetPayload payload = mapper.readValue(
                RoadNetTemplates.WAREHOUSE_WORKSHOP, RoadNetPayload.class);
        RoadNet net = RoadNet.of(
                payload.nodes().stream()
                        .map(n -> new NetNode(n.id(), n.x(), n.y(), NodeType.valueOf(n.type()),
                                n.capacity(), n.serviceTicks()))
                        .toList(),
                payload.edges().stream()
                        .map(e -> new NetEdge(e.id(), e.from(), e.to(), e.length(),
                                EdgeDirection.valueOf(e.direction()), e.speed(), e.capacity(),
                                "DUAL".equalsIgnoreCase(e.laneMode()) ? LaneMode.DUAL : LaneMode.SINGLE))
                        .toList());

        List<Task> tasks = new ArrayList<>();
        String[][] flows = {
                {"P1", "L1", "LOAD"}, {"P2", "L2", "LOAD"}, {"P3", "L3", "LOAD"},
                {"L1", "P1", "UNLOAD"}, {"L2", "P2", "UNLOAD"}, {"L3", "P3", "UNLOAD"},
                {"S1", "L1", "LOAD"}, {"S2", "L2", "LOAD"},
                {"P1", "S1", "LOAD"}, {"P2", "S2", "LOAD"}
        };
        for (int i = 0; i < 20; i++) {
            String[] flow = flows[i % flows.length];
            tasks.add(new Task(i + 1, flow[0], flow[1], TaskOperation.valueOf(flow[2])));
        }

        List<String> lines = new ArrayList<>();
        for (int count : new int[]{2, 3, 5, 8}) {
            for (String algorithm : new String[]{"nearest", "greedy-eta"}) {
                SimulationResult result = new SimulationEngine(net, tasks,
                        SimConfig.defaults(count, 20261107L, algorithm)).runToCompletion();
                lines.add(String.format("%d\t%s\tdone=%s\tcompleted=%d/%d\tmakespan=%d\tpath=%.1f\twait=%d\tconflict=%d\tticks=%d",
                        count, algorithm, result.finished(), result.completedTasks(), tasks.size(),
                        result.makespan(), result.totalPathLen(), result.totalWaitTick(),
                        result.conflictCount(), result.ticks()));
                assertTrue(result.finished(), count + " 台车 + " + algorithm + " 未能跑完");
                assertEquals(20, result.completedTasks());
                assertTrue(result.makespan() > 0);
            }
        }
        Files.write(Path.of("target", "warehouse-scale-matrix.txt"), lines);
    }
}
