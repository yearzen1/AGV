package com.agv.algorithm.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 逐段滚动预约表：记录每个资源（节点 ID 或弧 ID）被哪些 AGV 在哪些时间区间占用。
 * <p>
 * 只做判定与登记，不负责释放策略。AGV 每进入一条弧才登记，而不是出发前预约全程，
 * 因此等待会在节点上自然发生（对应实施计划 3.2 的"逐段滚动预约"）。
 */
public final class ReservationTable {

    /** 资源 ID -> 占用记录 */
    private final Map<String, List<Hold>> holds = new HashMap<>();

    /**
     * 区间 [start, end) 内是否还能容纳一台车。
     *
     * @param capacity 资源容量；节点固定为 1
     */
    public boolean canReserve(String resourceId, long start, long end, int capacity) {
        return canReserve(resourceId, start, end, capacity, null);
    }

    /**
     * 同 {@link #canReserve(String, long, long, int)}，但忽略指定 AGV 自己的占用。
     * 用于车辆离开节点后保留清空时间、又可能重新进入该节点的场景。
     */
    public boolean canReserve(String resourceId, long start, long end, int capacity, String ignoreAgvId) {
        int occupied = 0;
        for (Hold hold : holds.getOrDefault(resourceId, List.of())) {
            if (ignoreAgvId != null && hold.agvId().equals(ignoreAgvId)) {
                continue;
            }
            if (hold.interval().overlaps(new Interval(start, end))) {
                occupied++;
            }
        }
        return occupied < capacity;
    }

    public void reserve(String agvId, String resourceId, long start, long end) {
        holds.computeIfAbsent(resourceId, k -> new ArrayList<>())
                .add(new Hold(agvId, new Interval(start, end)));
    }

    /** 释放某台车在某个资源上的全部占用。 */
    public void release(String resourceId, String agvId) {
        List<Hold> list = holds.get(resourceId);
        if (list == null) {
            return;
        }
        list.removeIf(hold -> hold.agvId().equals(agvId));
        if (list.isEmpty()) {
            holds.remove(resourceId);
        }
    }

    /** 当前时刻占用某资源的车。 */
    public Set<String> holdersAt(String resourceId, long tick) {
        Set<String> result = new LinkedHashSet<>();
        for (Hold hold : holds.getOrDefault(resourceId, List.of())) {
            if (hold.interval().contains(tick)) {
                result.add(hold.agvId());
            }
        }
        return result;
    }

    /** 清空所有占用，用于 reset。 */
    public void clear() {
        holds.clear();
    }

    public boolean isFree(String resourceId) {
        return holds.getOrDefault(resourceId, List.of()).isEmpty();
    }

    private record Hold(String agvId, Interval interval) {
    }
}
