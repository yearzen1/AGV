package com.agv.algorithm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetValidatorTest {

    private static NetNode node(String id, NodeType type, double x, double y) {
        return new NetNode(id, x, y, type);
    }

    private static NetEdge twoWay(String id, String from, String to, double length) {
        return new NetEdge(id, from, to, length, EdgeDirection.TWO_WAY, 1.0, 1);
    }

    private static boolean hasCode(ValidationResult result, String code) {
        return result.errors().stream().anyMatch(e -> e.code().equals(code));
    }

    @Test
    @DisplayName("合法路网：充电位可达全部工位")
    void validNetworkPasses() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("J1", NodeType.JUNCTION, 5, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0)),
                List.of(twoWay("E1", "CH1", "J1", 5), twoWay("E2", "J1", "W1", 5)));

        assertTrue(result.valid(), () -> "预期合法，实际错误：" + result.errors());
    }

    @Test
    @DisplayName("工位没有让位空间：只给警告，不拦截保存")
    void warnsStationWithoutSiding() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("J1", NodeType.JUNCTION, 5, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0)),
                List.of(twoWay("E1", "CH1", "J1", 5), twoWay("E2", "J1", "W1", 5)));

        assertTrue(result.valid(), "警告不应让路网变成非法");
        assertTrue(result.warnings().stream()
                        .anyMatch(e -> e.code().equals(RoadNetValidator.WORKSTATION_NO_SIDING)
                                && "W1".equals(e.nodeId())),
                () -> "应提示工位没有让位空间，实际：" + result.warnings());
        assertTrue(result.all().stream().anyMatch(e -> e.severity() == ValidationError.Severity.WARNING),
                "合并列表里应能区分出警告");
    }

    @Test
    @DisplayName("工位相邻缓存位：不再告警")
    void stationWithBufferNeighbourHasNoWarning() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("J1", NodeType.JUNCTION, 5, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0),
                        node("B1", NodeType.BUFFER, 10, 4)),
                List.of(twoWay("E1", "CH1", "J1", 5), twoWay("E2", "J1", "W1", 5),
                        twoWay("E3", "W1", "B1", 4)));

        assertTrue(result.valid());
        assertTrue(result.warnings().stream()
                        .noneMatch(e -> e.code().equals(RoadNetValidator.WORKSTATION_NO_SIDING)),
                () -> "相邻有缓存位时不该告警，实际：" + result.warnings());
    }

    @Test
    @DisplayName("工位接近边是双车道：不再告警")
    void stationWithDualLaneApproachHasNoWarning() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("J1", NodeType.JUNCTION, 5, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0)),
                List.of(twoWay("E1", "CH1", "J1", 5),
                        new NetEdge("E2", "J1", "W1", 5, EdgeDirection.TWO_WAY, 1.0, 1, LaneMode.DUAL)));

        assertTrue(result.valid());
        assertTrue(result.warnings().stream()
                        .noneMatch(e -> e.code().equals(RoadNetValidator.WORKSTATION_NO_SIDING)),
                () -> "接近边双车道时不该告警，实际：" + result.warnings());
    }

    @Test
    @DisplayName("没有充电位：拦截")
    void rejectsNetworkWithoutCharger() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("W1", NodeType.WORKSTATION, 0, 0), node("W2", NodeType.WORKSTATION, 5, 0)),
                List.of(twoWay("E1", "W1", "W2", 5)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.NO_CHARGER));
    }

    @Test
    @DisplayName("工位不可达：拦截并给出工位 ID")
    void rejectsDisconnectedWorkstation() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 5, 0),
                        node("W2", NodeType.WORKSTATION, 99, 99)),
                List.of(twoWay("E1", "CH1", "W1", 5)));

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(RoadNetValidator.NOT_CONNECTED)
                        && "W2".equals(e.nodeId())));
    }

    @Test
    @DisplayName("同一对节点重复建边：拦截")
    void rejectsDuplicateEdgePair() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 5, 0)),
                List.of(twoWay("E1", "CH1", "W1", 5),
                        twoWay("E2", "W1", "CH1", 5)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.DUP_EDGE_PAIR));
    }

    @Test
    @DisplayName("长度非正：拦截")
    void rejectsNonPositiveLength() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 5, 0)),
                List.of(twoWay("E1", "CH1", "W1", 0)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.INVALID_NUMBER));
    }

    @Test
    @DisplayName("单向边造成死胡同：拦截")
    void rejectsDeadEnd() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("W1", NodeType.WORKSTATION, 5, 0)),
                List.of(new NetEdge("E1", "CH1", "W1", 5, EdgeDirection.ONE_WAY, 1.0, 1)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.DEAD_END));
    }

    @Test
    @DisplayName("缓存位串在主通道上：拦截")
    void rejectsBufferOnThroughPath() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0),
                        node("J1", NodeType.JUNCTION, 5, 0),
                        node("B1", NodeType.BUFFER, 7, 0),
                        node("W1", NodeType.WORKSTATION, 10, 0)),
                List.of(twoWay("E1", "CH1", "J1", 5),
                        twoWay("E2", "J1", "B1", 2),
                        twoWay("E3", "B1", "W1", 3)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.BUFFER_NOT_LEAF));
    }

    @Test
    @DisplayName("节点容量小于 1：拦截")
    void rejectsInvalidNodeCapacity() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(new NetNode("CH1", 0, 0, NodeType.CHARGER, 0),
                        node("W1", NodeType.WORKSTATION, 5, 0)),
                List.of(twoWay("E1", "CH1", "W1", 5)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.INVALID_NUMBER));
    }

    @Test
    @DisplayName("端点不存在：拦截")
    void rejectsMissingEndpoint() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0)),
                List.of(twoWay("E1", "CH1", "W9", 5)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.ENDPOINT_MISSING));
    }
}
