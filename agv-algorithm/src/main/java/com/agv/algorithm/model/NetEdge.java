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
 * @param capacity  每个方向可同时容纳的 AGV 数，必须 >= 1；单车道始终按 1 处理
 * @param laneMode  车道模式；旧数据缺省为 SINGLE
 */
public record NetEdge(String id, String from, String to, double length,
                      EdgeDirection direction, double speed, int capacity,
                      LaneMode laneMode) {

    public NetEdge {
        laneMode = laneMode == null ? LaneMode.SINGLE : laneMode;
    }

    /** 兼容旧构造：默认单车道。 */
    public NetEdge(String id, String from, String to, double length,
                   EdgeDirection direction, double speed, int capacity) {
        this(id, from, to, length, direction, speed, capacity, LaneMode.SINGLE);
    }

    public boolean isTwoWay() {
        return direction == EdgeDirection.TWO_WAY;
    }

    /** 只有双向路段才存在双车道；单向一律按单车道。 */
    public boolean isDualLane() {
        return direction == EdgeDirection.TWO_WAY && laneMode == LaneMode.DUAL;
    }
}
