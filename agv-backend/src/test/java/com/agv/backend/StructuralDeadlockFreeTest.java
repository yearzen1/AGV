package com.agv.backend;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.model.RoadNetCapacity;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.algorithm.sim.Task;
import com.agv.backend.support.DemoScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构无死锁矩阵：单向环上，车队数从 1 到上限、两种算法、20 个种子都必须跑完。
 * <p>
 * 同时把每次运行的指标写到 {@code target/structural-matrix.txt}，用于对比「删除兜底代码前后」是否逐位一致。
 */
class StructuralDeadlockFreeTest {

    /** 固定种子，覆盖不同的任务分配顺序与等待时序。 */
    private static final long[] SEEDS = {
            20261107L, 1L, 2L, 3L, 7L, 11L, 13L, 17L, 19L, 23L,
            29L, 31L, 37L, 41L, 43L, 47L, 53L, 59L, 61L, 67L
    };

    private static final List<String> ALGORITHMS = List.of("nearest", "greedy-eta");

    @Test
    @DisplayName("单向环：车队数从 1 到上限、两种算法、20 个种子全部跑完 20 单")
    void everyFleetSizeCompletesAllTasks() throws Exception {
        RoadNet net = DemoScenario.standardNet();
        List<Task> tasks = DemoScenario.demoTasks();
        int cap = RoadNetCapacity.maxAgvCount(net);
        assertEquals(6, cap, "标准车间两个充电位各留一个过道位，最多 6 台");

        List<String> lines = new ArrayList<>();
        for (int count = 1; count <= cap; count++) {
            for (String algorithm : ALGORITHMS) {
                for (long seed : SEEDS) {
                    SimulationResult result = new SimulationEngine(net, tasks,
                            SimConfig.defaults(count, seed, algorithm)).runToCompletion();

                    String label = count + " 台 / " + algorithm + " / seed=" + seed;
                    assertTrue(result.finished(), label + " 应跑完");
                    assertEquals(tasks.size(), result.completedTasks(), label + " 应完成全部任务");
                    assertTrue(result.makespan() > 0, label + " 完成时间应大于 0");

                    lines.add(String.format("%d\t%s\t%d\tcompleted=%d/%d\tmakespan=%d\tpath=%.1f\t"
                                    + "wait=%d\tconflict=%d\tavgDelivery=%d\tticks=%d",
                            count, algorithm, seed,
                            result.completedTasks(), tasks.size(), result.makespan(),
                            result.totalPathLen(), result.totalWaitTick(), result.conflictCount(),
                            result.avgDeliveryTick(), result.ticks()));
                }
            }
        }
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "structural-matrix.txt"), lines);
    }
}
