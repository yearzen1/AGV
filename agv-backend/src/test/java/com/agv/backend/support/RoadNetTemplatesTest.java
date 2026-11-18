package com.agv.backend.support;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.LaneMode;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.model.RoadNetCapacity;
import com.agv.algorithm.model.RoadNetValidator;
import com.agv.algorithm.model.ValidationResult;
import com.agv.backend.dto.RoadNetPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 预置模板必须是合法的单向环，否则启动即校验失败或跑出死锁。 */
class RoadNetTemplatesTest {

    private static RoadNet toModel(String json) throws Exception {
        RoadNetPayload payload = new ObjectMapper().readValue(json, RoadNetPayload.class);
        return RoadNet.of(
                payload.nodes().stream()
                        .map(n -> new NetNode(n.id(), n.x(), n.y(), NodeType.valueOf(n.type()), n.capacity()))
                        .toList(),
                payload.edges().stream()
                        .map(e -> new NetEdge(e.id(), e.from(), e.to(), e.length(),
                                EdgeDirection.valueOf(e.direction()), e.speed(), e.capacity(),
                                "DUAL".equalsIgnoreCase(e.laneMode()) ? LaneMode.DUAL : LaneMode.SINGLE))
                        .toList());
    }

    private static ValidationResult validate(String json) throws Exception {
        RoadNetPayload payload = new ObjectMapper().readValue(json, RoadNetPayload.class);
        return RoadNetValidator.validate(
                payload.nodes().stream()
                        .map(n -> new NetNode(n.id(), n.x(), n.y(), NodeType.valueOf(n.type()), n.capacity()))
                        .toList(),
                payload.edges().stream()
                        .map(e -> new NetEdge(e.id(), e.from(), e.to(), e.length(),
                                EdgeDirection.valueOf(e.direction()), e.speed(), e.capacity(),
                                "DUAL".equalsIgnoreCase(e.laneMode()) ? LaneMode.DUAL : LaneMode.SINGLE))
                        .toList());
    }

    @Test
    @DisplayName("标准车间（单向环）：8 节点 8 路段，环容量 14")
    void standardWorkshopIsOneWayRing() throws Exception {
        ValidationResult result = validate(RoadNetTemplates.STANDARD_WORKSHOP);
        assertTrue(result.valid(), () -> "模板必须通过校验，实际：" + result.errors());

        RoadNet net = toModel(RoadNetTemplates.STANDARD_WORKSHOP);
        assertEquals(8, net.nodes().size());
        assertEquals(8, net.edges().size());
        assertEquals(8, net.arcs().size(), "全部单向时，弧数等于路段数");
        assertEquals(14, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(6, RoadNetCapacity.maxAgvCount(net));
    }

    @Test
    @DisplayName("直线车间（单向往返环）：10 节点 10 路段，环容量 16")
    void lineWorkshopIsOneWayRing() throws Exception {
        ValidationResult result = validate(RoadNetTemplates.LINE_WORKSHOP);
        assertTrue(result.valid(), () -> "模板必须通过校验，实际：" + result.errors());

        RoadNet net = toModel(RoadNetTemplates.LINE_WORKSHOP);
        assertEquals(10, net.nodes().size());
        assertEquals(10, net.edges().size());
        assertEquals(16, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(6, RoadNetCapacity.maxAgvCount(net));
    }

    @Test
    @DisplayName("高容量车间（宽工位单向环）：工位容量 2 抬高环容量")
    void wideStationWorkshopRaisesCycleCapacity() throws Exception {
        ValidationResult result = validate(RoadNetTemplates.WIDE_STATION_WORKSHOP);
        assertTrue(result.valid(), () -> "模板必须通过校验，实际：" + result.errors());

        RoadNet net = toModel(RoadNetTemplates.WIDE_STATION_WORKSHOP);
        assertEquals(17, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(6, RoadNetCapacity.maxAgvCount(net));
    }
}
