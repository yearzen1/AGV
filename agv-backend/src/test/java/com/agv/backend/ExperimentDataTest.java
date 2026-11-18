package com.agv.backend;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.model.RoadNetCapacity;
import com.agv.algorithm.sim.AgvSnapshot;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.algorithm.sim.Task;
import com.agv.backend.support.DemoScenario;
import com.agv.backend.support.LegacyTwoWayWorkshop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 论文第 6 章的数据生成器：把规模对比、可复现性、物理超容采样写成 {@code target/thesis-data.txt}。
 * <p>
 * 同时充当回归：单向环上 1~6 台、两种算法、20 个种子必须全部完成 20 单。
 */
class ExperimentDataTest {

    private static final long[] SEEDS = {
            20261107L, 1L, 2L, 3L, 7L, 11L, 13L, 17L, 19L, 23L,
            29L, 31L, 37L, 41L, 43L, 47L, 53L, 59L, 61L, 67L
    };

    private static final List<String> ALGORITHMS = List.of("nearest", "greedy-eta");

    private static SimulationResult run(RoadNet net, List<Task> tasks, int cars, long seed, String algorithm) {
        return new SimulationEngine(net, tasks, SimConfig.defaults(cars, seed, algorithm))
                .runToCompletion();
    }

    private static String row(int cars, String algorithm, SimulationResult r) {
        return String.format("%d\t%s\t%d\t%.1f\t%d\t%d\t%d\t%d",
                cars, algorithm, r.makespan(), r.totalPathLen(), r.totalWaitTick(),
                r.conflictCount(), r.avgDeliveryTick(), r.ticks());
    }

    @Test
    @DisplayName("生成第 6 章数据：1~6 台 × 两种算法 × 20 种子全部完成")
    void generateThesisData() throws Exception {
        RoadNet net = DemoScenario.standardNet();
        List<Task> tasks = DemoScenario.demoTasks();
        int cap = RoadNetCapacity.maxAgvCount(net);
        assertEquals(6, cap);

        List<String> out = new ArrayList<>();

        // 1) 主表：固定种子
        out.add("# main seed=20261107");
        out.add("cars\talgorithm\tmakespan\tpath\twait\tconflict\tavgDelivery\tticks");
        for (int cars = 1; cars <= cap; cars++) {
            for (String algorithm : ALGORITHMS) {
                SimulationResult r = run(net, tasks, cars, DemoScenario.SEED, algorithm);
                assertTrue(r.finished(), cars + " 台 / " + algorithm + " 应完成");
                assertEquals(tasks.size(), r.completedTasks());
                out.add(row(cars, algorithm, r));
            }
        }

        // 2) 20 个种子的均值与极差
        out.add("# mean over 20 seeds");
        out.add("cars\talgorithm\tmakespanMean\tmakespanMin\tmakespanMax\tpathMean\twaitMean\tconflictMean\tavgDeliveryMean");
        for (int cars = 1; cars <= cap; cars++) {
            for (String algorithm : ALGORITHMS) {
                long makespanSum = 0;
                long makespanMin = Long.MAX_VALUE;
                long makespanMax = Long.MIN_VALUE;
                double pathSum = 0;
                long waitSum = 0;
                long conflictSum = 0;
                long deliverySum = 0;
                for (long seed : SEEDS) {
                    SimulationResult r = run(net, tasks, cars, seed, algorithm);
                    assertEquals(tasks.size(), r.completedTasks(),
                            cars + " 台 / " + algorithm + " / seed=" + seed + " 应完成");
                    makespanSum += r.makespan();
                    makespanMin = Math.min(makespanMin, r.makespan());
                    makespanMax = Math.max(makespanMax, r.makespan());
                    pathSum += r.totalPathLen();
                    waitSum += r.totalWaitTick();
                    conflictSum += r.conflictCount();
                    deliverySum += r.avgDeliveryTick();
                }
                int n = SEEDS.length;
                out.add(String.format("%d\t%s\t%.1f\t%d\t%d\t%.1f\t%.1f\t%.1f\t%.1f",
                        cars, algorithm,
                        makespanSum / (double) n, makespanMin, makespanMax,
                        pathSum / n, waitSum / (double) n, conflictSum / (double) n,
                        deliverySum / (double) n));
            }
        }

        // 3) 可复现性：同配置跑两次
        out.add("# determinism");
        out.add("cars\talgorithm\tmakespanA\tmakespanB\tpathA\tpathB\tconflictA\tconflictB\tidentical");
        for (int cars : new int[]{3, 6}) {
            for (String algorithm : ALGORITHMS) {
                SimulationResult a = run(net, tasks, cars, DemoScenario.SEED, algorithm);
                SimulationResult b = run(net, tasks, cars, DemoScenario.SEED, algorithm);
                boolean same = a.makespan() == b.makespan()
                        && Math.abs(a.totalPathLen() - b.totalPathLen()) < 1e-9
                        && a.conflictCount() == b.conflictCount();
                out.add(String.format("%d\t%s\t%d\t%d\t%.1f\t%.1f\t%d\t%d\t%s",
                        cars, algorithm, a.makespan(), b.makespan(),
                        a.totalPathLen(), b.totalPathLen(),
                        a.conflictCount(), b.conflictCount(), same));
                assertTrue(same, "同种子两次运行应完全一致");
            }
        }

        // 4) 物理超容采样
        out.add("# over-capacity ticks");
        out.add("cars\talgorithm\toverTicks");
        for (int cars = 1; cars <= cap; cars++) {
            for (String algorithm : ALGORITHMS) {
                out.add(String.format("%d\t%s\t%d", cars, algorithm, overCapacityTicks(net, tasks, cars, algorithm)));
            }
        }

        // 5) 加权环容量与车队上限
        out.add("# capacity");
        out.add("minCycleCapacity\tmaxAgvCount\tparkingSlots");
        out.add(String.format("%d\t%d\t%d", RoadNetCapacity.minCycleCapacity(net),
                RoadNetCapacity.maxAgvCount(net), RoadNetCapacity.parkingSlots(net)));

        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "thesis-data.txt"), out);
    }

    @Test
    @DisplayName("生成对照数据：旧双向标准车间与双向走廊")
    void generateLegacyComparison() throws Exception {
        List<String> out = new ArrayList<>();
        out.add("# legacy two-way standard workshop (14 nodes, 15 two-way edges)");
        out.add("cars\talgorithm\tfinished\tcompleted\tmakespan\tpath\twait\tconflict\tticks\toverTicks");
        RoadNet legacy = DemoScenario.toModel(LegacyTwoWayWorkshop.JSON);
        List<Task> tasks = DemoScenario.demoTasks();
        for (int cars = 2; cars <= 6; cars++) {
            for (String algorithm : ALGORITHMS) {
                SimulationResult r = run(legacy, tasks, cars, DemoScenario.SEED, algorithm);
                out.add(String.format("%d\t%s\t%s\t%d\t%d\t%.1f\t%d\t%d\t%d\t%d",
                        cars, algorithm, r.finished(), r.completedTasks(), r.makespan(),
                        r.totalPathLen(), r.totalWaitTick(), r.conflictCount(), r.ticks(),
                        overCapacityTicks(legacy, tasks, cars, algorithm)));
            }
        }

        out.add("# two-way corridor vs one-way ring, 2 cars, tasks X->Y and Y->X");
        out.add("topology\tfinished\tcompleted\tmakespan\tticks");
        RoadNet corridor = twoWayCorridor();
        SimulationResult corridorResult = run(corridor,
                List.of(new Task(1, "X", "Y"), new Task(2, "Y", "X")), 2, 1L, "nearest");
        out.add(String.format("two-way-corridor\t%s\t%d\t%d\t%d",
                corridorResult.finished(), corridorResult.completedTasks(),
                corridorResult.makespan(), corridorResult.ticks()));

        SimulationResult ringResult = run(DemoScenario.standardNet(),
                List.of(new Task(1, "W1", "W2"), new Task(2, "W2", "W1")), 2, 1L, "nearest");
        out.add(String.format("one-way-ring\t%s\t%d\t%d\t%d",
                ringResult.finished(), ringResult.completedTasks(),
                ringResult.makespan(), ringResult.ticks()));

        Files.write(Path.of("target", "thesis-legacy.txt"), out);
    }

    /** 4 节点双向走廊：CH1 — X — Y — CH2。 */
    private RoadNet twoWayCorridor() {
        return RoadNet.of(
                List.of(new com.agv.algorithm.model.NetNode("CH1", 0, 0,
                                com.agv.algorithm.model.NodeType.CHARGER, 2),
                        new com.agv.algorithm.model.NetNode("X", 10, 0,
                                com.agv.algorithm.model.NodeType.WORKSTATION, 1),
                        new com.agv.algorithm.model.NetNode("Y", 20, 0,
                                com.agv.algorithm.model.NodeType.WORKSTATION, 1),
                        new com.agv.algorithm.model.NetNode("CH2", 30, 0,
                                com.agv.algorithm.model.NodeType.CHARGER, 2)),
                List.of(new com.agv.algorithm.model.NetEdge("E1", "CH1", "X", 10,
                                com.agv.algorithm.model.EdgeDirection.TWO_WAY, 1.0, 1),
                        new com.agv.algorithm.model.NetEdge("E2", "X", "Y", 10,
                                com.agv.algorithm.model.EdgeDirection.TWO_WAY, 1.0, 1),
                        new com.agv.algorithm.model.NetEdge("E3", "Y", "CH2", 10,
                                com.agv.algorithm.model.EdgeDirection.TWO_WAY, 1.0, 1)));
    }

    /** 逐帧采样节点停靠数，返回超过容量的 tick 数。 */
    private int overCapacityTicks(RoadNet net, List<Task> tasks, int cars, String algorithm) {
        Map<String, Integer> capacity = new HashMap<>();
        net.nodes().values().forEach(node -> capacity.put(node.id(), node.capacity()));
        SimulationEngine engine = new SimulationEngine(net, tasks,
                SimConfig.defaults(cars, DemoScenario.SEED, algorithm));

        int overTicks = 0;
        int guard = 0;
        while (!engine.finished() && guard++ < 200_000) {
            engine.step();
            Map<String, Integer> byNode = new HashMap<>();
            for (AgvSnapshot snapshot : engine.snapshots()) {
                if (snapshot.arcId() == null && snapshot.nodeId() != null) {
                    byNode.merge(snapshot.nodeId(), 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> entry : byNode.entrySet()) {
                if (entry.getValue() > capacity.getOrDefault(entry.getKey(), 1)) {
                    overTicks++;
                }
            }
        }
        return overTicks;
    }
}
