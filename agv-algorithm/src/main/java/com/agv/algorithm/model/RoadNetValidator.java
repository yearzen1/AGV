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
 * 路网合法性校验，对应实施计划第 4 节的 7 条规则。
 * <p>
 * 前端在保存前调用做软提示，后端在入库前调用做硬拦截；错误信息带 nodeId/edgeId 供前端定位高亮。
 */
public final class RoadNetValidator {

    public static final String DUP_NODE_ID = "DUP_NODE_ID";
    public static final String DUP_EDGE_ID = "DUP_EDGE_ID";
    public static final String ENDPOINT_MISSING = "ENDPOINT_MISSING";
    public static final String NO_CHARGER = "NO_CHARGER";
    public static final String NOT_CONNECTED = "NOT_CONNECTED";
    public static final String DEAD_END = "DEAD_END";
    public static final String BUFFER_NOT_LEAF = "BUFFER_NOT_LEAF";
    public static final String INVALID_NUMBER = "INVALID_NUMBER";
    public static final String DUP_EDGE_PAIR = "DUP_EDGE_PAIR";
    public static final String SELF_LOOP = "SELF_LOOP";
    public static final String EMPTY = "EMPTY";

    private RoadNetValidator() {
    }

    public static ValidationResult validate(List<NetNode> nodes, List<NetEdge> edges) {
        List<ValidationError> errors = new ArrayList<>();

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

        // 6. 数值合法
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

        // 3. 端点存在 + 禁止自环
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

        // 4. 同一对节点之间只保留一条路段（无向比较）
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

        // 5. 至少 1 个充电位
        long chargerCount = nodes.stream().filter(n -> n.type() == NodeType.CHARGER).count();
        if (chargerCount == 0) {
            errors.add(ValidationError.of(NO_CHARGER, "路网至少需要一个充电位作为 AGV 初始停放位"));
        }

        // 端点错误时不再做强连通判断，避免噪音
        boolean endpointsOk = errors.stream().noneMatch(e -> ENDPOINT_MISSING.equals(e.code()));
        if (!endpointsOk) {
            return new ValidationResult(errors);
        }

        RoadNet net = RoadNet.of(nodes, edges);

        // 7. 不允许"只进不出"的死胡同
        for (NetNode node : nodes) {
            if (net.arcsFrom(node.id()).isEmpty() && !net.arcsTo(node.id()).isEmpty()) {
                errors.add(ValidationError.node(DEAD_END, "节点只进不出（死胡同）：" + node.id(), node.id()));
            }
        }

        // 7.5 缓存位应在支路末端，不能串在主通道
        for (NetNode node : nodes) {
            if (node.type() != NodeType.BUFFER) {
                continue;
            }
            long degree = edges.stream()
                    .filter(edge -> edge.from().equals(node.id()) || edge.to().equals(node.id()))
                    .count();
            if (degree != 1) {
                errors.add(ValidationError.node(BUFFER_NOT_LEAF,
                        "缓存位应位于支路末端，只连接一条路段：" + node.id(), node.id()));
            }
        }

        // 8. 连通性：任一充电位可达全部工位
        List<NetNode> chargers = net.nodes().values().stream()
                .filter(n -> n.type() == NodeType.CHARGER).toList();
        List<NetNode> workstations = net.nodes().values().stream()
                .filter(n -> n.type() == NodeType.WORKSTATION).toList();

        for (NetNode charger : chargers) {
            Set<String> reachable = reachableFrom(net, charger.id());
            for (NetNode station : workstations) {
                if (!reachable.contains(station.id())) {
                    errors.add(ValidationError.node(NOT_CONNECTED,
                            "充电位 " + charger.id() + " 无法到达工位 " + station.id(), station.id()));
                }
            }
        }

        return new ValidationResult(errors);
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
