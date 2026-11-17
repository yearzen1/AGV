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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规模实验里暴露过的两个回归点：回充选位必须看剩余容量，缓存位等待不能反复往返。
 */
class SimulationRegressionTest {

    private static SimulationResult run(SimulationEngine engine, List<SimEvent> events) {
        int guard = 0;
        while (!engine.finished() && guard++ < 200_000) {
            events.addAll(engine.step().events());
        }
        assertTrue(engine.finished(), "仿真应自然结束");
        return engine.runToCompletion();
    }

    @Test
    @DisplayName("8 台贪心回充：两个容量 4 的充电位都应停满，不能有车永远等 CH1")
    void allAgvsReachChargingWhenChargersHaveRoom() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(8, DemoScenario.SEED, "greedy-eta"));
        SimulationResult result = run(engine, new ArrayList<>());

        long charging = engine.snapshots().stream()
                .filter(snapshot -> snapshot.status() == AgvStatus.CHARGING)
                .count();
        List<String> notCharging = engine.snapshots().stream()
                .filter(snapshot -> snapshot.status() != AgvStatus.CHARGING)
                .map(snapshot -> snapshot.id() + ":" + snapshot.status() + "@" + snapshot.nodeId())
                .toList();

        assertEquals(20, result.completedTasks());
        assertEquals(8, charging, "全部车都应回到充电位，实际未回充：" + notCharging);
    }

    @Test
    @DisplayName("5 台就近：缓存位可以让多台车分时复用，但不能无限往返")
    void bufferYieldStaysBounded() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(5, DemoScenario.SEED, "nearest"));
        List<SimEvent> events = new ArrayList<>();
        SimulationResult result = run(engine, events);

        List<SimEvent> yields = events.stream()
                .filter(event -> event.type() == SimEvent.Type.YIELD_BUFFER)
                .toList();
        Map<String, Integer> byAgvAndPlace = new HashMap<>();
        for (SimEvent event : yields) {
            byAgvAndPlace.merge(event.agvId() + "@" + event.place(), 1, Integer::sum);
        }
        int maxAtSamePlace = byAgvAndPlace.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        assertTrue(yields.size() > 0, "标准车间应有让位动作");
        // 一个工位只有一个缓存位，多台车排队时只能分时复用；这里守住「不失控」的上限，
        // 而不是消灭让位，否则 8 台就近的 makespan 会从 3035 恶化到 3292 以上。
        assertTrue(yields.size() <= 60, "让位次数失控：" + byAgvAndPlace);
        assertTrue(maxAtSamePlace <= 8, "同一车在同一缓存位反复让位：" + byAgvAndPlace);
        assertFalse(result.finished() && result.completedTasks() < 20);
    }

    @Test
    @DisplayName("让位动作不计入冲突次数，冲突事件只对应真正停住的等待")
    void yieldIsNotCountedAsConflict() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(5, DemoScenario.SEED, "nearest"));
        List<SimEvent> events = new ArrayList<>();
        SimulationResult result = run(engine, events);

        List<SimEvent> yields = events.stream()
                .filter(event -> event.type() == SimEvent.Type.YIELD_BUFFER)
                .toList();
        for (SimEvent yield : yields) {
            boolean sameTickConflict = events.stream().anyMatch(event ->
                    event.type() == SimEvent.Type.WAIT_CONFLICT
                            && event.tick() == yield.tick()
                            && event.agvId().equals(yield.agvId()));
            assertFalse(sameTickConflict, "让位与冲突等待不应同时发生：" + yield);
        }

        long conflictEvents = events.stream()
                .filter(event -> event.type() == SimEvent.Type.WAIT_CONFLICT)
                .count();
        assertEquals(conflictEvents, result.conflictCount(), "冲突事件数与汇总指标应一致");
    }
}
