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
 * 规模实验里暴露过的回归点：严格容量、回充选位、缓存位让位、死锁停止。
 */
class SimulationRegressionTest {

    private static SimulationResult run(SimulationEngine engine, List<SimEvent> events) {
        int guard = 0;
        while (!engine.finished() && guard++ < 200_000) {
            events.addAll(engine.step().events());
        }
        assertTrue(engine.finished(), "仿真应结束（完成或死锁）");
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

        assertFalse(result.deadlocked(), "3 台应能跑完");
        assertEquals(20, result.completedTasks());
        assertEquals(3, charging, "全部车都应回到充电位，实际未回充：" + notCharging);
    }

    @Test
    @DisplayName("3 台就近：缓存位让位有上限，不能无限往返")
    void bufferYieldStaysBounded() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(3, DemoScenario.SEED, "nearest"));
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

        assertFalse(result.deadlocked(), "3 台应能跑完");
        assertEquals(20, result.completedTasks());
        assertTrue(yields.size() > 0, "标准车间应有让位动作");
        assertTrue(yields.size() <= 20, "让位次数失控：" + byAgvAndPlace);
        assertTrue(maxAtSamePlace <= 5, "同一车在同一缓存位反复让位：" + byAgvAndPlace);
    }

    @Test
    @DisplayName("让位动作不计入冲突次数，冲突事件只对应真正停住的等待")
    void yieldIsNotCountedAsConflict() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(3, DemoScenario.SEED, "nearest"));
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

    @Test
    @DisplayName("严格容量：3 台完成 20 单，任何 tick 工位停靠数都不超过容量")
    void strictCapacityDoesNotOverfillNodes() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(3, DemoScenario.SEED, "nearest"));
        Map<String, Integer> capacity = new HashMap<>();
        capacity.put("CH1", 4);
        capacity.put("CH2", 4);
        for (String node : List.of("J1", "J2", "J3", "J4", "J5", "B1", "B2", "B3", "B4", "W1", "W2", "W3")) {
            capacity.put(node, 1);
        }
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
        assertFalse(result.deadlocked(), "3 台应能跑完");
        assertEquals(20, result.completedTasks());
        assertEquals(0, overTicks, "严格容量下不应出现节点停靠数超过容量");
    }

    @Test
    @DisplayName("严格容量：5 台在无任务进展 2000 tick 后判定死锁并停止")
    void strictCapacityStopsAtDeadlock() throws Exception {
        SimulationEngine engine = new SimulationEngine(DemoScenario.standardNet(), DemoScenario.demoTasks(),
                SimConfig.defaults(5, DemoScenario.SEED, "nearest"));
        List<SimEvent> events = new ArrayList<>();
        SimulationResult result = run(engine, events);

        assertTrue(result.deadlocked(), "5 台在单缓存位严格容量下应死锁");
        assertTrue(result.completedTasks() < 20, "死锁时不应全部完成");
        assertTrue(result.ticks() <= result.makespan() + 2_100,
                "死锁应在最后一单完成 2000 tick 后尽快停止，实际 " + result.ticks()
                        + "，最后完成 " + result.makespan());
        boolean explained = events.stream().anyMatch(event ->
                event.type() == SimEvent.Type.FINISHED && event.detail().contains("死锁"));
        assertTrue(explained, "结束事件应说明死锁原因");
    }
}
