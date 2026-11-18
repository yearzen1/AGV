package com.agv.algorithm.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 路网合法性校验。保存前调用，错误直接拦截。
 * <p>
 * 规则围绕「结构无死锁」组织：全部路段单向、所有节点度数 ≥ 2、有向图强连通。
 * 这三条成立且车队数不超过加权环容量减一，等待图就不可能成环。
 * 推导见 {@code docs/explanation/结构无死锁设计.md}。
 * <p>
 * 前端在保存前调用做软提示，后端在入库前调用做硬拦截；错误信息带 nodeId/edgeId 供前端定位高亮。
 */
public final class RoadNetValidator {

    public static final String DUP_NODE_ID = "DUP_NODE_ID";
    public static final String DUP_EDGE_ID = "DUP_EDGE_ID";
    public static final String ENDPOINT_MISSING = "ENDPOINT_MISSING";
    public static final String NO_CHARGER = "NO_CHARGER";
    public static final String INVALID_NUMBER = "INVALID_NUMBER";
    public static final String DUP_EDGE_PAIR = "DUP_EDGE_PAIR";
    public static final String SELF_LOOP = "SELF_LOOP";
    public static final String EMPTY = "EMPTY";
    /** 存在双向路段：两台车即可在一条边上来回互等。 */
    public static final String TWO_WAY_EDGE = "TWO_WAY_EDGE";
    /** 节点度数小于 2：叶子支路只有一个出入口。 */
    public static final String LEAF_NODE = "LEAF_NODE";
    /** 有向图不强连通：存在无法互相到达的节点。 */
    public static final String NOT_STRONGLY_CONNECTED = "NOT_STRONGLY_CONNECTED";

    private RoadNetValidator() {
    }

    public static ValidationResult validate(List<NetNode> nodes, List<NetEdge> edges) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationError> warnings = new ArrayList<>();

        if (nodes == null || nodes.isEmpty()) {
            return new ValidationResult(List.of(ValidationError.of(EMPTY, "路网为空，至少需要一个充电位")));
        }

        // 1. 节点 ID 唯一
        Set<String> nodeIds = new HashSet<>();
        for (NetNode node : nodes) {
            if (!nodeIds.add(node.id())) {
                errors.add(ValidationError.node(DUP_NODE_ID, "节点编号重复：" + node.id(), node.id()));
            }
        }

        // 2. 路段 ID 唯一
        Set<String> edgeIds = new HashSet<>();
        for (NetEdge edge : edges) {
            if (!edgeIds.add(edge.id())) {
                errors.add(ValidationError.edge(DUP_EDGE_ID, "路段编号重复：" + edge.id(), edge.id()));
            }
        }

        // 3. 数值合法
        for (NetNode node : nodes) {
            if (!Double.isFinite(node.x()) || !Double.isFinite(node.y())) {
                errors.add(ValidationError.node(INVALID_NUMBER, "节点坐标非法：" + node.id(), node.id()));
            }
            if (node.capacity() < 1) {
                errors.add(ValidationError.node(INVALID_NUMBER, "节点容量必须不小于 1：" + node.id(), node.id()));
            }
        }
        for (NetEdge edge : edges) {
            if (!(edge.length() > 0) || !Double.isFinite(edge.length())) {
                errors.add(ValidationError.edge(INVALID_NUMBER, "路段长度必须大于 0：" + edge.id(), edge.id()));
            }
            if (!(edge.speed() > 0) || !Double.isFinite(edge.speed())) {
                errors.add(ValidationError.edge(INVALID_NUMBER, "路段速度必须大于 0：" + edge.id(), edge.id()));
            }
            if (edge.capacity() < 1) {
                errors.add(ValidationError.edge(INVALID_NUMBER, "路段容量必须不小于 1：" + edge.id(), edge.id()));
            }
        }

        // 4. 端点存在 + 禁止自环
        for (NetEdge edge : edges) {
            if (!nodeIds.contains(edge.from())) {
                errors.add(ValidationError.edge(ENDPOINT_MISSING, "路段起点不存在：" + edge.from(), edge.id()));
            }
            if (!nodeIds.contains(edge.to())) {
                errors.add(ValidationError.edge(ENDPOINT_MISSING, "路段终点不存在：" + edge.to(), edge.id()));
            }
            if (Objects.equals(edge.from(), edge.to())) {
                errors.add(ValidationError.edge(SELF_LOOP, "路段两端不能是同一节点：" + edge.id(), edge.id()));
            }
        }

        // 5. 同一对节点之间只保留一条路段（无向比较）
        Map<String, String> pairToEdge = new HashMap<>();
        for (NetEdge edge : edges) {
            if (Objects.equals(edge.from(), edge.to())) {
                continue;
            }
            String key = edge.from().compareTo(edge.to()) <= 0
                    ? edge.from() + "|" + edge.to()
                    : edge.to() + "|" + edge.from();
            String previous = pairToEdge.putIfAbsent(key, edge.id());
            if (previous != null) {
                errors.add(ValidationError.edge(DUP_EDGE_PAIR,
                        "节点 " + edge.from() + " 与 " + edge.to() + " 之间重复建边：" + previous + " / " + edge.id(),
                        edge.id()));
            }
        }

        // 6. 至少 1 个充电位
        long chargerCount = nodes.stream().filter(n -> n.type() == NodeType.CHARGER).count();
        if (chargerCount == 0) {
            errors.add(ValidationError.of(NO_CHARGER, "路网至少需要一个充电位作为 AGV 初始停放位"));
        }

        // 端点错误时不再做拓扑判断，避免噪音
        boolean endpointsOk = errors.stream().noneMatch(e -> ENDPOINT_MISSING.equals(e.code()));
        if (!endpointsOk) {
            return new ValidationResult(errors);
        }

        RoadNet net = RoadNet.of(nodes, edges);

        // 7. 结构无死锁：全部单向
        for (NetEdge edge : edges) {
            if (edge.isTwoWay()) {
                errors.add(ValidationError.edge(TWO_WAY_EDGE,
                        "路段 " + edge.id() + " 是双向：双向边会让两台车在一条路上互相等，只允许单向", edge.id()));
            }
        }

        // 8. 结构无死锁：没有叶子节点
        Map<String, Long> degree = new HashMap<>();
        for (NetEdge edge : edges) {
            degree.merge(edge.from(), 1L, Long::sum);
            degree.merge(edge.to(), 1L, Long::sum);
        }
        for (NetNode node : nodes) {
            long incident = degree.getOrDefault(node.id(), 0L);
            if (incident < 2) {
                errors.add(ValidationError.node(LEAF_NODE,
                        "节点 " + node.id() + " 只连接 " + incident + " 条路段：叶子支路只有一个出入口，"
                                + "里面的车和门口的车会互等，请把它放到环上", node.id()));
            }
        }

        // 9. 结构无死锁：强连通
        String unreachableFrom = null;
        String unreachableTo = null;
        for (NetNode node : nodes) {
            Set<String> reachable = reachableFrom(net, node.id());
            for (NetNode other : nodes) {
                if (!reachable.contains(other.id())) {
                    unreachableFrom = node.id();
                    unreachableTo = other.id();
                    break;
                }
            }
            if (unreachableFrom != null) {
                break;
            }
        }
        if (unreachableFrom != null) {
            errors.add(ValidationError.node(NOT_STRONGLY_CONNECTED,
                    "节点 " + unreachableFrom + " 无法到达节点 " + unreachableTo
                            + "：单向路网必须强连通，车要能绕回所有节点", unreachableFrom));
        }

        return new ValidationResult(errors, warnings);
    }

    /** 有向图上的可达集合（BFS）。 */
    public static Set<String> reachableFrom(RoadNet net, String startNodeId) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(startNodeId);
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Arc arc : net.arcsFrom(current)) {
                if (visited.add(arc.to())) {
                    queue.add(arc.to());
                }
            }
        }
        return visited;
    }
}
