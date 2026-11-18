package com.agv.backend;

import com.agv.algorithm.sim.AgvSnapshot;
import com.agv.algorithm.sim.AgvStatus;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimEvent;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.backend.support.DemoScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构无死锁落地后仍要守住的回归点：严格容量、回充选位、冲突计数口径。
 */
class SimulationRegressionTest {

    private static SimulationResult run(SimulationEngine engine, List<SimEvent> events) {
        int guard = 0;
        while (!engine.finished() && guard++ < 200_000) {
            events.addAll(engine.step().events());
        }
        assertTrue(engine.finished(), "仿真应跑完");
        return engine.runToCompletion();
    }

    @Test
    @DisplayName("3 台贪心回充：所有车都应停进还有空位的充电位")
    void allAgvsReachChargingWhenChargersHaveRoom() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(3, DemoScenario.SEED, "greedy-eta"));
        SimulationResult result = run(engine, new ArrayList<>());

        long charging = engine.snapshots().stream()
                .filter(snapshot -> snapshot.status() == AgvStatus.CHARGING)
                .count();
        List<String> notCharging = engine.snapshots().stream()
                .filter(snapshot -> snapshot.status() != AgvStatus.CHARGING)
                .map(snapshot -> snapshot.id() + ":" + snapshot.status() + "@" + snapshot.nodeId())
                .toList();

        assertEquals(20, result.completedTasks());
        assertEquals(3, charging, "全部车都应回到充电位，实际未回充：" + notCharging);
    }

    @Test
    @DisplayName("严格容量：3 台完成 20 单，任何 tick 节点停靠数都不超过容量")
    void strictCapacityDoesNotOverfillNodes() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(3, DemoScenario.SEED, "nearest"));
        Map<String, Integer> capacity = Map.of(
                "CH1", 4, "CH2", 4,
                "J1", 1, "J2", 1, "J3", 1,
                "W1", 1, "W2", 1, "W3", 1);

        int guard = 0;
        int overTicks = 0;
        while (!engine.finished() && guard++ < 20_000) {
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
        SimulationResult result = engine.runToCompletion();
        assertEquals(20, result.completedTasks());
        assertEquals(0, overTicks, "严格容量下不应出现节点停靠数超过容量");
    }

    @Test
    @DisplayName("冲突事件数与汇总指标一致，且每次连续等待只记一次")
    void conflictEventsMatchMetrics() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(3, DemoScenario.SEED, "nearest"));
        List<SimEvent> events = new ArrayList<>();
        SimulationResult result = run(engine, events);

        long conflictEvents = events.stream()
                .filter(event -> event.type() == SimEvent.Type.WAIT_CONFLICT)
                .filter(event -> event.tick() <= result.makespan())
                .count();
        assertEquals(conflictEvents, result.conflictCount(),
                "最后一单完成前的冲突事件数应与汇总指标一致（之后的回充冲突不计入指标）");
    }
}
