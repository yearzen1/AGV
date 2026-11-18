package com.agv.algorithm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetValidatorTest {

    private static NetNode node(String id, NodeType type, double x, double y) {
        return new NetNode(id, x, y, type);
    }

    private static NetEdge oneWay(String id, String from, String to, double length) {
        return new NetEdge(id, from, to, length, EdgeDirection.ONE_WAY, 1.0, 1);
    }

    private static NetEdge twoWay(String id, String from, String to, double length) {
        return new NetEdge(id, from, to, length, EdgeDirection.TWO_WAY, 1.0, 1);
    }

    private static boolean hasCode(ValidationResult result, String code) {
        return result.errors().stream().anyMatch(e -> e.code().equals(code));
    }

    /** 四个节点组成的最小合法单向环，充电位在环上。 */
    private static List<NetNode> ringNodes() {
        return List.of(node("CH1", NodeType.CHARGER, 0, 0),
                node("J1", NodeType.JUNCTION, 5, 0),
                node("W1", NodeType.WORKSTATION, 10, 0),
                node("J2", NodeType.JUNCTION, 5, 5));
    }

    private static List<NetEdge> ringEdges() {
        return List.of(oneWay("E1", "CH1", "J1", 5),
                oneWay("E2", "J1", "W1", 5),
                oneWay("E3", "W1", "J2", 5),
                oneWay("E4", "J2", "CH1", 5));
    }

    @Test
    @DisplayName("合法单向环：通过，且没有警告")
    void validOneWayRingPasses() {
        ValidationResult result = RoadNetValidator.validate(ringNodes(), ringEdges());

        assertTrue(result.valid(), () -> "预期合法，实际错误：" + result.errors());
        assertTrue(result.warnings().isEmpty(), () -> "不该有警告，实际：" + result.warnings());
    }

    @Test
    @DisplayName("存在双向路段：拦截")
    void rejectsTwoWayEdge() {
        List<NetEdge> edges = new ArrayList<>(ringEdges());
        edges.set(0, twoWay("E1", "CH1", "J1", 5));

        ValidationResult result = RoadNetValidator.validate(ringNodes(), edges);

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.TWO_WAY_EDGE));
        assertTrue(result.errors().stream()
                        .anyMatch(e -> RoadNetValidator.TWO_WAY_EDGE.equals(e.code())
                                && "E1".equals(e.edgeId())),
                () -> "错误应定位到双向路段，实际：" + result.errors());
    }

    @Test
    @DisplayName("叶子节点：拦截")
    void rejectsLeafNode() {
        List<NetNode> nodes = new ArrayList<>(ringNodes());
        nodes.add(node("W9", NodeType.WORKSTATION, 15, 0));
        List<NetEdge> edges = new ArrayList<>(ringEdges());
        edges.add(oneWay("E5", "W1", "W9", 5));

        ValidationResult result = RoadNetValidator.validate(nodes, edges);

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.LEAF_NODE));
        assertTrue(result.errors().stream()
                        .anyMatch(e -> RoadNetValidator.LEAF_NODE.equals(e.code())
                                && "W9".equals(e.nodeId())),
                () -> "错误应定位到叶子节点，实际：" + result.errors());
    }

    @Test
    @DisplayName("两个环只用一个方向相连：不强连通，拦截")
    void rejectsNotStronglyConnected() {
        List<NetNode> nodes = List.of(
                node("CH1", NodeType.CHARGER, 0, 0),
                node("A", NodeType.JUNCTION, 5, 0),
                node("B", NodeType.JUNCTION, 0, 5),
                node("W1", NodeType.WORKSTATION, 10, 5),
                node("C", NodeType.JUNCTION, 15, 0),
                node("D", NodeType.JUNCTION, 20, 5));
        List<NetEdge> edges = List.of(
                oneWay("E1", "CH1", "A", 5),
                oneWay("E2", "A", "B", 5),
                oneWay("E3", "B", "CH1", 5),
                oneWay("E4", "A", "W1", 5),
                oneWay("E5", "W1", "C", 5),
                oneWay("E6", "C", "D", 5),
                oneWay("E7", "D", "W1", 5));

        ValidationResult result = RoadNetValidator.validate(nodes, edges);

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.NOT_STRONGLY_CONNECTED));
    }

    @Test
    @DisplayName("没有充电位：拦截")
    void rejectsNetworkWithoutCharger() {
        List<NetNode> nodes = List.of(
                node("W1", NodeType.WORKSTATION, 0, 0),
                node("J1", NodeType.JUNCTION, 5, 0),
                node("W2", NodeType.WORKSTATION, 5, 5));
        List<NetEdge> edges = List.of(
                oneWay("E1", "W1", "J1", 5),
                oneWay("E2", "J1", "W2", 5),
                oneWay("E3", "W2", "W1", 5));

        ValidationResult result = RoadNetValidator.validate(nodes, edges);

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.NO_CHARGER));
    }

    @Test
    @DisplayName("同一对节点重复建边：拦截")
    void rejectsDuplicateEdgePair() {
        ValidationResult result = RoadNetValidator.validate(ringNodes(),
                List.of(oneWay("E1", "CH1", "J1", 5),
                        oneWay("E2", "J1", "W1", 5),
                        oneWay("E3", "W1", "J2", 5),
                        oneWay("E4", "J2", "CH1", 5),
                        oneWay("E5", "J1", "CH1", 5)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.DUP_EDGE_PAIR));
    }

    @Test
    @DisplayName("长度非正：拦截")
    void rejectsNonPositiveLength() {
        ValidationResult result = RoadNetValidator.validate(ringNodes(),
                List.of(oneWay("E1", "CH1", "J1", 0),
                        oneWay("E2", "J1", "W1", 5),
                        oneWay("E3", "W1", "J2", 5),
                        oneWay("E4", "J2", "CH1", 5)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.INVALID_NUMBER));
    }

    @Test
    @DisplayName("节点容量小于 1：拦截")
    void rejectsInvalidNodeCapacity() {
        List<NetNode> nodes = new ArrayList<>(ringNodes());
        nodes.set(0, new NetNode("CH1", 0, 0, NodeType.CHARGER, 0));

        ValidationResult result = RoadNetValidator.validate(nodes, ringEdges());

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.INVALID_NUMBER));
    }

    @Test
    @DisplayName("端点不存在：拦截")
    void rejectsMissingEndpoint() {
        ValidationResult result = RoadNetValidator.validate(
                List.of(node("CH1", NodeType.CHARGER, 0, 0)),
                List.of(oneWay("E1", "CH1", "W9", 5)));

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.ENDPOINT_MISSING));
    }

    @Test
    @DisplayName("空路网：拦截")
    void rejectsEmptyNetwork() {
        ValidationResult result = RoadNetValidator.validate(List.of(), List.of());

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.EMPTY));
    }

    @Test
    @DisplayName("编号重复：拦截")
    void rejectsDuplicateIds() {
        List<NetNode> nodes = new ArrayList<>(ringNodes());
        nodes.add(node("J1", NodeType.JUNCTION, 9, 9));
        List<NetEdge> edges = new ArrayList<>(ringEdges());
        edges.add(oneWay("E1", "W1", "J2", 5));

        ValidationResult result = RoadNetValidator.validate(nodes, edges);

        assertFalse(result.valid());
        assertTrue(hasCode(result, RoadNetValidator.DUP_NODE_ID));
        assertTrue(hasCode(result, RoadNetValidator.DUP_EDGE_ID));
    }
}
