package com.agv.algorithm.model;

/**
 * 路网节点。
 *
 * @param id   节点编号，全图唯一
 * @param x    平面坐标 X（米）
 * @param y    平面坐标 Y（米）
 * @param type 节点类型
 */
public record NetNode(String id, double x, double y, NodeType type) {

    public double distanceTo(NetNode other) {
        return Math.hypot(x - other.x, y - other.y);
    }
}
