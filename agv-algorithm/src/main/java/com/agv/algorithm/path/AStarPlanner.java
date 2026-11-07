package com.agv.algorithm.path;

import com.agv.algorithm.model.Arc;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.RoadNet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 图上 A*（邻接表 + 状态含入边）。
 * <p>
 * 代价单位为秒：{@code g = Σ(length/speed) + 转弯惩罚}；
 * 启发式 {@code h = 欧氏距离 / 全图最大速度}，与入边无关，满足可采纳性与一致性。
 * <p>
 * 转弯惩罚只在入弧与出弧夹角超过阈值时累加一次。因为代价依赖入边，
 * 搜索状态必须是 {@link SearchState}（nodeId + inArcId），而不是裸 nodeId。
 */
public final class AStarPlanner {

    private static final double EPS = 1e-9;

    private final double turnPenaltySeconds;
    private final double turnThresholdDegrees;

    public AStarPlanner(double turnPenaltySeconds, double turnThresholdDegrees) {
        if (turnPenaltySeconds < 0) {
            throw new IllegalArgumentException("转弯惩罚不能为负");
        }
        if (turnThresholdDegrees < 0 || turnThresholdDegrees > 180) {
            throw new IllegalArgumentException("转弯判定角度必须在 [0,180]");
        }
        this.turnPenaltySeconds = turnPenaltySeconds;
        this.turnThresholdDegrees = turnThresholdDegrees;
    }

    /** 默认：超过 45° 记 1 秒转弯惩罚。 */
    public static AStarPlanner defaultPlanner() {
        return new AStarPlanner(1.0, 45.0);
    }

    public double turnPenaltySeconds() {
        return turnPenaltySeconds;
    }

    public double turnThresholdDegrees() {
        return turnThresholdDegrees;
    }

    public PathResult plan(RoadNet net, String startNodeId, String goalNodeId) {
        return plan(net, startNodeId, goalNodeId, Set.of());
    }

    /**
     * @param blockedEdgeIds 被封锁的物理路段 ID 集合（双向边会同时封掉正反两条弧）
     */
    public PathResult plan(RoadNet net, String startNodeId, String goalNodeId, Set<String> blockedEdgeIds) {
        NetNode startNode = net.node(startNodeId);
        NetNode goalNode = net.node(goalNodeId);
        if (startNode == null) {
            throw new IllegalArgumentException("起点不存在：" + startNodeId);
        }
        if (goalNode == null) {
            throw new IllegalArgumentException("终点不存在：" + goalNodeId);
        }
        if (startNodeId.equals(goalNodeId)) {
            return new PathResult(List.of(), 0.0, 0.0, 0, true);
        }
        Set<String> blocked = blockedEdgeIds == null ? Set.of() : blockedEdgeIds;

        SearchState startState = new SearchState(startNodeId, null);
        Map<SearchState, Double> bestG = new HashMap<>();
        Map<SearchState, SearchState> parent = new HashMap<>();
        Map<SearchState, Arc> cameBy = new HashMap<>();
        Set<SearchState> closed = new HashSet<>();

        PriorityQueue<OpenEntry> open = new PriorityQueue<>(
                Comparator.comparingDouble(OpenEntry::f).thenComparingDouble(OpenEntry::g));

        bestG.put(startState, 0.0);
        open.add(new OpenEntry(heuristic(startNode, goalNode, net), 0.0, startState));

        int expanded = 0;
        while (!open.isEmpty()) {
            OpenEntry current = open.poll();
            if (closed.contains(current.state())) {
                continue;
            }
            if (current.g() > bestG.getOrDefault(current.state(), Double.POSITIVE_INFINITY) + EPS) {
                continue;
            }
            closed.add(current.state());
            expanded++;

            if (current.state().nodeId().equals(goalNodeId)) {
                List<Arc> path = reconstruct(current.state(), parent, cameBy);
                double length = path.stream().mapToDouble(Arc::length).sum();
                return new PathResult(path, current.g(), length, expanded, true);
            }

            Arc inArc = current.state().inArcId() == null ? null : net.arc(current.state().inArcId());
            for (Arc next : net.arcsFrom(current.state().nodeId())) {
                if (blocked.contains(next.edgeId())) {
                    continue;
                }
                double stepCost = next.travelSeconds() + turnPenalty(net, inArc, next);
                double tentativeG = current.g() + stepCost;
                SearchState nextState = new SearchState(next.to(), next.id());
                if (tentativeG + EPS < bestG.getOrDefault(nextState, Double.POSITIVE_INFINITY)) {
                    bestG.put(nextState, tentativeG);
                    parent.put(nextState, current.state());
                    cameBy.put(nextState, next);
                    NetNode nextNode = net.node(next.to());
                    open.add(new OpenEntry(tentativeG + heuristic(nextNode, goalNode, net),
                            tentativeG, nextState));
                }
            }
        }
        return PathResult.notFound(expanded);
    }

    private double heuristic(NetNode from, NetNode goal, RoadNet net) {
        return from.distanceTo(goal) / net.maxSpeed();
    }

    private double turnPenalty(RoadNet net, Arc in, Arc out) {
        if (in == null) {
            return 0.0;
        }
        NetNode inFrom = net.node(in.from());
        NetNode inTo = net.node(in.to());
        NetNode outFrom = net.node(out.from());
        NetNode outTo = net.node(out.to());
        double v1x = inTo.x() - inFrom.x();
        double v1y = inTo.y() - inFrom.y();
        double v2x = outTo.x() - outFrom.x();
        double v2y = outTo.y() - outFrom.y();
        double n1 = Math.hypot(v1x, v1y);
        double n2 = Math.hypot(v2x, v2y);
        if (n1 < EPS || n2 < EPS) {
            return 0.0;
        }
        double cos = (v1x * v2x + v1y * v2y) / (n1 * n2);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        double angle = Math.toDegrees(Math.acos(cos));
        return angle > turnThresholdDegrees ? turnPenaltySeconds : 0.0;
    }

    private List<Arc> reconstruct(SearchState end, Map<SearchState, SearchState> parent,
                                  Map<SearchState, Arc> cameBy) {
        List<Arc> reversed = new ArrayList<>();
        SearchState cursor = end;
        while (parent.containsKey(cursor)) {
            reversed.add(cameBy.get(cursor));
            cursor = parent.get(cursor);
        }
        List<Arc> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    private record OpenEntry(double f, double g, SearchState state) {
    }
}
