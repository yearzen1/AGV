package com.agv.algorithm.model;

/**
 * 路网节点。
 *
 * @param id           节点编号，全图唯一
 * @param x            平面坐标 X（米）
 * @param y            平面坐标 Y（米）
 * @param type         节点类型
 * @param capacity     节点可同时容纳的 AGV 数，默认 1
 * @param serviceTicks 该节点装货或卸货占用的 tick 数；0 表示沿用全局装货时间或瞬时卸货
 */
public record NetNode(String id, double x, double y, NodeType type,
                      int capacity, int serviceTicks) {

    public NetNode(String id, double x, double y, NodeType type, int capacity) {
        this(id, x, y, type, capacity, 0);
    }

    public NetNode(String id, double x, double y, NodeType type) {
        this(id, x, y, type, 1, 0);
    }

    /** 该节点是否可作为任务的服务端点。 */
    public boolean isServiceNode() {
        return type == NodeType.PICKING || type == NodeType.LOADING || type == NodeType.STORAGE;
    }

    public double distanceTo(NetNode other) {
        return Math.hypot(x - other.x, y - other.y);
    }
}
