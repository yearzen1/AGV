package com.agv.algorithm.model;

/**
 * 路网节点。
 *
 * @param id       节点编号，全图唯一
 * @param x        平面坐标 X（米）
 * @param y        平面坐标 Y（米）
 * @param type     节点类型
 * @param capacity 节点可同时容纳的 AGV 数，默认 1
 */
public record NetNode(String id, double x, double y, NodeType type, int capacity) {

    public NetNode(String id, double x, double y, NodeType type) {
        this(id, x, y, type, 1);
    }

    public double distanceTo(NetNode other) {
        return Math.hypot(x - other.x, y - other.y);
    }
}
