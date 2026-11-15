package com.agv.algorithm.sim;

import java.util.List;

/**
 * 一帧仿真数据，对应 WebSocket 推送的 {@code {tick, agvs, events}}。
 */
public record SimFrame(long tick, List<AgvSnapshot> agvs, List<SimEvent> events,
                       boolean finished, Metrics metrics) {

    public SimFrame {
        agvs = List.copyOf(agvs);
        events = List.copyOf(events);
    }

    /**
     * 实时指标。{@code makespan} 在最后一件任务完成时定格，之后不变。
     * {@code avgDeliveryTick} 是所有已完成任务的「完成 tick − 分配 tick」平均值，没有已完成任务时为 0。
     */
    public record Metrics(long makespan, double totalPathLen, long totalWaitTick,
                          int conflictCount, int completedTasks, int totalTasks,
                          long avgDeliveryTick) {
    }
}
