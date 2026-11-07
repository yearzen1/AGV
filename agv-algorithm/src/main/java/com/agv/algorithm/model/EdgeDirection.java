package com.agv.algorithm.model;

/**
 * 路段方向。双向边在加载时拆成两条有向弧（{@code E1+} / {@code E1-}）。
 */
public enum EdgeDirection {
    ONE_WAY,
    TWO_WAY
}
