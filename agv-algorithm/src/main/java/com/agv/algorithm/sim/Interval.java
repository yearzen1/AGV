package com.agv.algorithm.sim;

/**
 * 半开区间 [start, end)，单位为 tick。{@link #FOREVER} 表示占用到显式释放为止（节点占用用）。
 */
public record Interval(long start, long end) {

    public static final long FOREVER = Long.MAX_VALUE;

    public Interval {
        if (end < start) {
            throw new IllegalArgumentException("区间结束早于开始：" + start + " > " + end);
        }
    }

    public boolean overlaps(Interval other) {
        return start < other.end && other.start < end;
    }

    public boolean contains(long t) {
        return start <= t && t < end;
    }
}
