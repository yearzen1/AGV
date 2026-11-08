package com.agv.algorithm.model;

/**
 * 车道模式。
 * <p>
 * {@link #SINGLE} 表示整条物理路段同一时刻只允许一辆车（窄巷道双向也走这一模式）；
 * {@link #DUAL} 表示双向各有独立车道，仅对 {@link EdgeDirection#TWO_WAY} 生效。
 */
public enum LaneMode {
    SINGLE,
    DUAL
}
