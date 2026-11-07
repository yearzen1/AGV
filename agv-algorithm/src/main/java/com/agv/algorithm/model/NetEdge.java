package com.agv.algorithm.model;

/**
 * 路段（无向定义）。真正的搜索与调度落在拆分后的有向弧 {@link Arc} 上。
 *
 * @param id        路段编号，全图唯一
 * @param from      起点节点 ID
 * @param to        终点节点 ID
 * @param length    路段长度（米），必须 > 0；单向/双向两条弧共用同一长度
 * @param direction 方向
 * @param speed     行驶速度（米/秒），必须 > 0
 * @param capacity  路段容量（可同时容纳的 AGV 数），必须 >= 1
 */
public record NetEdge(String id, String from, String to, double length,
                      EdgeDirection direction, double speed, int capacity) {

    public boolean isTwoWay() {
        return direction == EdgeDirection.TWO_WAY;
    }
}
