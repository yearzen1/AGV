package com.agv.algorithm.path;

import com.agv.algorithm.model.Arc;
import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStarPlannerTest {

    private static final double EPS = 1e-6;

    private static NetNode node(String id, double x, double y) {
        return new NetNode(id, x, y, NodeType.JUNCTION);
    }

    private static NetEdge edge(String id, String from, String to, double length) {
        return new NetEdge(id, from, to, length, EdgeDirection.TWO_WAY, 1.0, 1);
    }

    /** A --- B --- C 直线，各段 10 m，速度 1 m/s。 */
    private RoadNet lineNet() {
        return RoadNet.of(
                List.of(node("A", 0, 0), node("B", 10, 0), node("C", 20, 0)),
                List.of(edge("E1", "A", "B", 10), edge("E2", "B", "C", 10)));
    }

    @Test
    @DisplayName("直线路网：A→C 应为两条弧，代价 20 秒")
    void findsPathOnLineNetwork() {
        PathResult result = AStarPlanner.defaultPlanner().plan(lineNet(), "A", "C");

        assertTrue(result.found());
        assertEquals(List.of("E1+", "E2+"), result.arcs().stream().map(Arc::id).toList());
        assertEquals(20.0, result.costSeconds(), EPS);
        assertEquals(20.0, result.totalLength(), EPS);
        assertEquals(List.of("A", "B", "C"), result.nodeIds());
    }

    @Test
    @DisplayName("起点等于终点：返回空路径且代价为 0")
    void startEqualsGoal() {
        PathResult result = AStarPlanner.defaultPlanner().plan(lineNet(), "B", "B");

        assertTrue(result.found());
        assertTrue(result.arcs().isEmpty());
        assertEquals(0.0, result.costSeconds(), EPS);
    }

    @Test
    @DisplayName("不可达：返回未找到而不是抛异常")
    void unreachableReturnsNotFound() {
        RoadNet net = RoadNet.of(
                List.of(node("A", 0, 0), node("B", 10, 0), node("Z", 99, 99)),
                List.of(edge("E1", "A", "B", 10)));

        PathResult result = AStarPlanner.defaultPlanner().plan(net, "A", "Z");

        assertFalse(result.found());
        assertTrue(result.arcs().isEmpty());
    }

    @Test
    @DisplayName("封锁路段：绕行或不可达")
    void blockedEdgeIsAvoided() {
        RoadNet net = RoadNet.of(
                List.of(node("A", 0, 0), node("B", 10, 0), node("C", 20, 0), node("D", 10, 10)),
                List.of(edge("E1", "A", "B", 10), edge("E2", "B", "C", 10),
                        edge("E3", "A", "D", 14.14), edge("E4", "D", "C", 14.14)));

        PathResult direct = AStarPlanner.defaultPlanner().plan(net, "A", "C");
        PathResult detour = AStarPlanner.defaultPlanner().plan(net, "A", "C", Set.of("E1", "E2"));

        assertEquals(20.0, direct.costSeconds(), EPS);
        assertTrue(detour.found());
        assertEquals(List.of("E3+", "E4+"), detour.arcs().stream().map(Arc::id).toList());
        assertTrue(detour.costSeconds() > direct.costSeconds());
    }

    @Test
    @DisplayName("转弯惩罚：L 型路径比纯行驶时间多 1 秒")
    void turnPenaltyIsApplied() {
        RoadNet net = RoadNet.of(
                List.of(node("A", 0, 0), node("B", 10, 0), node("C", 10, 10)),
                List.of(edge("E1", "A", "B", 10), edge("E2", "B", "C", 10)));

        PathResult withPenalty = AStarPlanner.defaultPlanner().plan(net, "A", "C");
        PathResult withoutPenalty = new AStarPlanner(0, 45).plan(net, "A", "C");

        assertEquals(21.0, withPenalty.costSeconds(), EPS);
        assertEquals(20.0, withoutPenalty.costSeconds(), EPS);
        assertEquals(20.0, withPenalty.totalLength(), EPS);
    }

    @Test
    @DisplayName("与状态空间 Bellman-Ford 参考实现对拍：随机路网上代价一致")
    void matchesReferenceOnRandomGraphs() {
        Random random = new Random(20261107L);
        for (int round = 0; round < 30; round++) {
            RoadNet net = randomNet(random);
            String start = "N0";
            String goal = "N" + (6 + random.nextInt(1));
            Set<String> blocked = random.nextBoolean()
                    ? Set.of()
                    : Set.of(net.arcs().get(random.nextInt(net.arcs().size())).edgeId());

            PathResult actual = AStarPlanner.defaultPlanner().plan(net, start, goal, blocked);
            double expected = referenceCost(net, start, goal, blocked);

            if (Double.isInfinite(expected)) {
                assertFalse(actual.found(), "参考实现不可达，A* 却找到了路径");
            } else {
                assertTrue(actual.found(), "参考实现可达，A* 未找到路径");
                assertEquals(expected, actual.costSeconds(), EPS, "第 " + round + " 轮代价不一致");
                assertPathIsContinuous(net, start, actual);
            }
        }
    }

    private void assertPathIsContinuous(RoadNet net, String start, PathResult result) {
        String cursor = start;
        for (Arc arc : result.arcs()) {
            assertEquals(cursor, arc.from(), "路径在 " + arc.id() + " 处断开");
            cursor = arc.to();
        }
        assertEquals(result.nodeIds().get(result.nodeIds().size() - 1), cursor);
    }

    private RoadNet randomNet(Random random) {
        int nodeCount = 7;
        List<NetNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(node("N" + i, random.nextDouble() * 20, random.nextDouble() * 20));
        }

        Set<String> pairs = new HashSet<>();
        List<NetEdge> edges = new ArrayList<>();
        int edgeSeq = 0;
        for (int i = 0; i < nodeCount - 1; i++) {
            edges.add(edge("R" + edgeSeq++, "N" + i, "N" + (i + 1), 1 + random.nextDouble() * 10));
            pairs.add(key("N" + i, "N" + (i + 1)));
        }
        int extra = 3 + random.nextInt(4);
        for (int k = 0; k < extra; k++) {
            int a = random.nextInt(nodeCount);
            int b = random.nextInt(nodeCount);
            if (a == b || pairs.contains(key("N" + a, "N" + b))) {
                continue;
            }
            pairs.add(key("N" + a, "N" + b));
            edges.add(new NetEdge("X" + edgeSeq++, "N" + a, "N" + b,
                    1 + random.nextDouble() * 10, EdgeDirection.TWO_WAY,
                    0.5 + random.nextDouble() * 1.5, 1));
        }
        return RoadNet.of(nodes, edges);
    }

    private String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** 独立参考实现：在 (node, inArc) 状态空间上做 Bellman-Ford 松弛。 */
    private double referenceCost(RoadNet net, String start, String goal, Set<String> blocked) {
        List<SearchState> states = new ArrayList<>();
        states.add(new SearchState(start, null));
        for (Arc arc : net.arcs()) {
            if (!blocked.contains(arc.edgeId())) {
                states.add(new SearchState(arc.to(), arc.id()));
            }
        }

        Map<SearchState, Double> dist = new HashMap<>();
        states.forEach(s -> dist.put(s, Double.POSITIVE_INFINITY));
        dist.put(new SearchState(start, null), 0.0);

        AStarPlanner penaltySource = AStarPlanner.defaultPlanner();
        for (int iteration = 0; iteration < states.size(); iteration++) {
            boolean changed = false;
            for (SearchState state : states) {
                double base = dist.get(state);
                if (Double.isInfinite(base)) {
                    continue;
                }
                Arc inArc = state.inArcId() == null ? null : net.arc(state.inArcId());
                for (Arc next : net.arcsFrom(state.nodeId())) {
                    if (blocked.contains(next.edgeId())) {
                        continue;
                    }
                    SearchState nextState = new SearchState(next.to(), next.id());
                    if (!dist.containsKey(nextState)) {
                        continue;
                    }
                    double candidate = base + next.travelSeconds() + penalty(penaltySource, net, inArc, next);
                    if (candidate + EPS < dist.get(nextState)) {
                        dist.put(nextState, candidate);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }

        double best = Double.POSITIVE_INFINITY;
        for (Map.Entry<SearchState, Double> entry : dist.entrySet()) {
            if (entry.getKey().nodeId().equals(goal)) {
                best = Math.min(best, entry.getValue());
            }
        }
        return best;
    }

    private double penalty(AStarPlanner planner, RoadNet net, Arc in, Arc out) {
        if (in == null) {
            return 0.0;
        }
        double v1x = net.node(in.to()).x() - net.node(in.from()).x();
        double v1y = net.node(in.to()).y() - net.node(in.from()).y();
        double v2x = net.node(out.to()).x() - net.node(out.from()).x();
        double v2y = net.node(out.to()).y() - net.node(out.from()).y();
        double n1 = Math.hypot(v1x, v1y);
        double n2 = Math.hypot(v2x, v2y);
        double cos = Math.max(-1, Math.min(1, (v1x * v2x + v1y * v2y) / (n1 * n2)));
        double degrees = Math.toDegrees(Math.acos(cos));
        return degrees > planner.turnThresholdDegrees() ? planner.turnPenaltySeconds() : 0.0;
    }
}
