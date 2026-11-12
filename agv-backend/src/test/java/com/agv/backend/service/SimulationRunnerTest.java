package com.agv.backend.service;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.Task;
import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.ws.SimSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationRunnerTest {

    /**
     * 复刻 ConcurrentWebSocketSessionDecorator 的行为：调度线程被中断时，阻塞发送会失败。
     * 真实场景里这会导致结束状态推不出去、会话被移除，页面显示「实时通道未连接」。
     */
    private static final class InterruptAwareHandler extends SimSocketHandler {
        private final List<Map<String, Object>> messages = new CopyOnWriteArrayList<>();

        InterruptAwareHandler() {
            super(new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public void sendToUser(Long userId, Object payload) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException(
                        "The current thread was interrupted while waiting for a blocking send to complete");
            }
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
    @DisplayName("仿真结束时先推送 FINISHED 状态，再停止调度线程")
    void finishIsBroadcastBeforeSchedulerShutdown() throws Exception {
        InterruptAwareHandler handler = new InterruptAwareHandler();
        ExperimentRepository experimentRepository = mock(ExperimentRepository.class);
        when(experimentRepository.findById(1L)).thenReturn(Optional.empty());
        ExperimentMetricRepository metricRepository = mock(ExperimentMetricRepository.class);

        SimulationEngine engine = new SimulationEngine(corridor(),
                List.of(new Task(1, "W1", "W2")),
                SimConfig.defaults(1, 1L, "nearest"));
        SimulationRunner runner = new SimulationRunner(1L, engine, 1L, 1L, 1L, "nearest", 1, 1L,
                handler, experimentRepository, metricRepository);
        try {
            runner.start();
            runner.setSpeed(8.0);
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline && !handler.sawStatus("FINISHED")) {
                Thread.sleep(50);
            }
            assertTrue(handler.sawStatus("FINISHED"),
                    "结束状态没有推送成功，会话会在调度线程被中断后掉线");
        } finally {
            runner.shutdown();
        }
    }

    /** CH1 — W1 — W2 — CH2 共 4 个节点、3 条双向边。 */
    private RoadNet corridor() {
        List<NetNode> nodes = List.of(
                new NetNode("CH1", 0, 0, NodeType.CHARGER),
                new NetNode("W1", 10, 0, NodeType.WORKSTATION),
                new NetNode("W2", 20, 0, NodeType.WORKSTATION),
                new NetNode("CH2", 30, 0, NodeType.CHARGER));
        List<NetEdge> edges = List.of(
                new NetEdge("E1", "CH1", "W1", 10, EdgeDirection.TWO_WAY, 1.0, 1),
                new NetEdge("E2", "W1", "W2", 10, EdgeDirection.TWO_WAY, 1.0, 1),
                new NetEdge("E3", "W2", "CH2", 10, EdgeDirection.TWO_WAY, 1.0, 1));
        return RoadNet.of(nodes, edges);
    }
}
