package com.agv.algorithm.model;

/**
 * 有向弧：路网在算法层的最小单位。双向边拆成 {@code edgeId+"+"} 与 {@code edgeId+"-"} 两条。
 *
 * @param id     弧 ID，形如 {@code E1+} / {@code E1-}
 * @param edgeId 所属物理路段 ID，相向冲突与边级预约按它判定
 * @param from   起点节点 ID
 * @param to     终点节点 ID
 * @param length 弧长（米）
 * @param speed  速度（米/秒）
 */
public record Arc(String id, String edgeId, String from, String to, double length, double speed) {

    /** 通过该弧所需时间（秒）。 */
    public double travelSeconds() {
        return length / speed;
    }

    public boolean isReverse() {
        return id.endsWith("-");
    }
}
