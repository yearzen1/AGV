package com.agv.algorithm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拓扑路网：节点 + 物理路段 + 拆好的有向弧 + 邻接表。
 * <p>
 * 这是全系统唯一的路网真源。构造时把双向边拆成 {@code E1+} / {@code E1-} 两条有向弧，
 * 之后 A*、时间窗、仿真引擎一律只认 {@link Arc}。
 * <p>
 * 构造不做合法性校验，校验请用 {@link RoadNetValidator}（前端软提示、后端硬拦截都调它）。
 */
public final class RoadNet {

    private final Map<String, NetNode> nodes;
    private final Map<String, NetEdge> edges;
    private final Map<String, Arc> arcsById;
    private final List<Arc> arcs;
    private final Map<String, List<Arc>> outArcs;
    private final Map<String, List<Arc>> inArcs;
    private final double maxSpeed;

    private RoadNet(Map<String, NetNode> nodes, Map<String, NetEdge> edges) {
        this.nodes = Collections.unmodifiableMap(nodes);
        this.edges = Collections.unmodifiableMap(edges);

        Map<String, Arc> byId = new LinkedHashMap<>();
        List<Arc> all = new ArrayList<>();
        Map<String, List<Arc>> out = new LinkedHashMap<>();
        Map<String, List<Arc>> in = new LinkedHashMap<>();
        nodes.keySet().forEach(id -> {
            out.put(id, new ArrayList<>());
            in.put(id, new ArrayList<>());
        });

        double fastest = 0.0;
        for (NetEdge edge : edges.values()) {
            Arc forward = new Arc(edge.id() + "+", edge.id(), edge.from(), edge.to(), edge.length(), edge.speed());
            byId.put(forward.id(), forward);
            all.add(forward);
            out.get(forward.from()).add(forward);
            in.get(forward.to()).add(forward);

            if (edge.isTwoWay()) {
                Arc reverse = new Arc(edge.id() + "-", edge.id(), edge.to(), edge.from(), edge.length(), edge.speed());
                byId.put(reverse.id(), reverse);
                all.add(reverse);
                out.get(reverse.from()).add(reverse);
                in.get(reverse.to()).add(reverse);
            }
            fastest = Math.max(fastest, edge.speed());
        }

        out.replaceAll((k, v) -> List.copyOf(v));
        in.replaceAll((k, v) -> List.copyOf(v));
        this.arcsById = Collections.unmodifiableMap(byId);
        this.arcs = List.copyOf(all);
        this.outArcs = Collections.unmodifiableMap(out);
        this.inArcs = Collections.unmodifiableMap(in);
        this.maxSpeed = fastest > 0 ? fastest : 1.0;
    }

    public static RoadNet of(List<NetNode> nodes, List<NetEdge> edges) {
        Map<String, NetNode> nodeMap = new LinkedHashMap<>();
        nodes.forEach(n -> nodeMap.put(n.id(), n));
        Map<String, NetEdge> edgeMap = new LinkedHashMap<>();
        edges.forEach(e -> edgeMap.put(e.id(), e));
        return new RoadNet(nodeMap, edgeMap);
    }

    public static RoadNet empty() {
        return new RoadNet(new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    public Map<String, NetNode> nodes() {
        return nodes;
    }

    public Map<String, NetEdge> edges() {
        return edges;
    }

    public List<Arc> arcs() {
        return arcs;
    }

    public NetNode node(String id) {
        return nodes.get(id);
    }

    public NetEdge edge(String id) {
        return edges.get(id);
    }

    public Arc arc(String id) {
        return arcsById.get(id);
    }

    /** 从某节点出发的所有有向弧。 */
    public List<Arc> arcsFrom(String nodeId) {
        return outArcs.getOrDefault(nodeId, List.of());
    }

    /** 进入某节点的所有有向弧。 */
    public List<Arc> arcsTo(String nodeId) {
        return inArcs.getOrDefault(nodeId, List.of());
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public long countByType(NodeType type) {
        return nodes.values().stream().filter(n -> n.type() == type).count();
    }
}
