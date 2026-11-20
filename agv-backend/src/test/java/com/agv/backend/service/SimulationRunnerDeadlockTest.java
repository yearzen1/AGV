package com.agv.backend.service;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.Task;
import com.agv.backend.entity.Experiment;
import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.ws.SimSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationRunnerDeadlockTest {

    private static final class StatusHandler extends SimSocketHandler {
        private final List<Map<String, Object>> messages = new CopyOnWriteArrayList<>();

        StatusHandler() {
            super(new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public void sendToUser(Long userId, Object payload) {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) payload;
            messages.add(message);
        }

        boolean sawStatus(String status) {
            return messages.stream().anyMatch(message ->
                    "status".equals(message.get("type")) && status.equals(message.get("status")));
        }
    }

    @Test
    @DisplayName("死锁：推送 DEADLOCK 状态、实验记为 DEADLOCK，且不写指标")
    void deadlockIsMarkedWithoutMetrics() throws Exception {
        RoadNet line = RoadNet.of(
                List.of(new NetNode("CH1", 0, 0, NodeType.CHARGER),
                        new NetNode("CH2", 10, 0, NodeType.CHARGER)),
                List.of(new NetEdge("E1", "CH1", "CH2", 10, EdgeDirection.TWO_WAY, 1.0, 1)));
        SimulationEngine engine = new SimulationEngine(line,
                List.of(new Task(1, "CH1", "CH2"), new Task(2, "CH2", "CH1")),
                SimConfig.defaults(2, 1L, "nearest"));
        engine.runToCompletion();
        assertTrue(engine.deadlocked(), "构造的走廊应进入死锁");

        StatusHandler handler = new StatusHandler();
        ExperimentRepository experimentRepository = mock(ExperimentRepository.class);
        Experiment experiment = new Experiment();
        experiment.setId(1L);
        experiment.setStatus("RUNNING");
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));
        ExperimentMetricRepository metricRepository = mock(ExperimentMetricRepository.class);

        SimulationRunner runner = new SimulationRunner(1L, engine, 1L, 1L, 1L, "nearest", 2, 1L,
                handler, experimentRepository, metricRepository, mock(SimulationEventService.class));
        try {
            runner.start();
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && !handler.sawStatus("DEADLOCK")) {
                Thread.sleep(50);
            }
            assertTrue(handler.sawStatus("DEADLOCK"), "应推送 DEADLOCK 状态");
            assertEquals("DEADLOCK", experiment.getStatus());
            verify(metricRepository, never()).save(org.mockito.ArgumentMatchers.any());
        } finally {
            runner.shutdown();
        }
    }
}
