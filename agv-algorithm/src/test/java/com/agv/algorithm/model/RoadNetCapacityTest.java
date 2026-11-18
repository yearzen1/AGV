package com.agv.algorithm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadNetCapacityTest {

    private static NetNode node(String id, NodeType type, int capacity) {
        return new NetNode(id, 0, 0, type, capacity);
    }

    private static NetEdge oneWay(String id, String from, String to) {
        return new NetEdge(id, from, to, 1.0, EdgeDirection.ONE_WAY, 1.0, 1);
    }

    /** 8 节点单环，两个充电位容量 4，其余节点容量 1。 */
    private RoadNet standardRing(int chargerCapacity) {
        return RoadNet.of(
                List.of(node("CH1", NodeType.CHARGER, chargerCapacity),
                        node("J1", NodeType.JUNCTION, 1),
                        node("W1", NodeType.WORKSTATION, 1),
                        node("W2", NodeType.WORKSTATION, 1),
                        node("W3", NodeType.WORKSTATION, 1),
                        node("J2", NodeType.JUNCTION, 1),
                        node("CH2", NodeType.CHARGER, chargerCapacity),
                        node("J3", NodeType.JUNCTION, 1)),
                List.of(oneWay("E1", "CH1", "J1"),
                        oneWay("E2", "J1", "W1"),
                        oneWay("E3", "W1", "W2"),
                        oneWay("E4", "W2", "W3"),
                        oneWay("E5", "W3", "J2"),
                        oneWay("E6", "J2", "CH2"),
                        oneWay("E7", "CH2", "J3"),
                        oneWay("E8", "J3", "CH1")));
    }

    @Test
    @DisplayName("八节点单环：环容量等于所有节点容量之和")
    void singleRingSumsNodeCapacity() {
        RoadNet net = standardRing(4);

        assertEquals(14, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(13, RoadNetCapacity.maxAgvCount(net));
    }

    @Test
    @DisplayName("充电位容量变化直接影响车队上限")
    void chargerCapacityChangesFleetCap() {
        assertEquals(7, RoadNetCapacity.maxAgvCount(standardRing(1)));
    }

    @Test
    @DisplayName("多个环取容量最小的那个")
    void picksSmallestCycle() {
        // 大环 A→B→C→D→A（4 个容量 1）+ 弦 A→C，形成 A→C→D→A（3 个容量 1）
        RoadNet net = RoadNet.of(
                List.of(node("A", NodeType.CHARGER, 1),
                        node("B", NodeType.JUNCTION, 1),
                        node("C", NodeType.JUNCTION, 1),
                        node("D", NodeType.WORKSTATION, 1)),
                List.of(oneWay("E1", "A", "B"),
                        oneWay("E2", "B", "C"),
                        oneWay("E3", "C", "D"),
                        oneWay("E4", "D", "A"),
                        oneWay("E5", "A", "C")));

        assertEquals(3, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(2, RoadNetCapacity.maxAgvCount(net));
    }

    @Test
    @DisplayName("两个节点互相指向：二环，容量按两个节点相加")
    void twoNodeCycle() {
        RoadNet net = RoadNet.of(
                List.of(node("A", NodeType.CHARGER, 2), node("B", NodeType.WORKSTATION, 3)),
                List.of(oneWay("E1", "A", "B"), oneWay("E2", "B", "A")));

        assertEquals(5, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(4, RoadNetCapacity.maxAgvCount(net));
    }

    @Test
    @DisplayName("无环图：没有环可以占满，车队上限为无穷")
    void acyclicGraphHasNoCap() {
        RoadNet net = RoadNet.of(
                List.of(node("A", NodeType.CHARGER, 1),
                        node("B", NodeType.JUNCTION, 1),
                        node("C", NodeType.WORKSTATION, 1)),
                List.of(oneWay("E1", "A", "B"), oneWay("E2", "B", "C")));

        assertEquals(Integer.MAX_VALUE, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(Integer.MAX_VALUE, RoadNetCapacity.maxAgvCount(net));
    }

    @Test
    @DisplayName("空图：没有环")
    void emptyNetHasNoCap() {
        assertEquals(Integer.MAX_VALUE, RoadNetCapacity.minCycleCapacity(RoadNet.empty()));
    }

    @Test
    @DisplayName("环中某个节点容量更大时，该环的容量按加总计算")
    void widerNodeRaisesCycleCapacity() {
        List<NetNode> nodes = new ArrayList<>(List.of(
                node("A", NodeType.CHARGER, 4),
                node("B", NodeType.WORKSTATION, 2),
                node("C", NodeType.JUNCTION, 1),
                node("D", NodeType.JUNCTION, 1)));
        RoadNet net = RoadNet.of(nodes, List.of(
                oneWay("E1", "A", "B"),
                oneWay("E2", "B", "C"),
                oneWay("E3", "C", "D"),
                oneWay("E4", "D", "A")));

        assertEquals(8, RoadNetCapacity.minCycleCapacity(net));
        assertEquals(7, RoadNetCapacity.maxAgvCount(net));
    }
}
