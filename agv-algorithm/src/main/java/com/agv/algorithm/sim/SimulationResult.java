package com.agv.algorithm.sim;

/**
 * 离线跑完一轮实验的结果。口径见实施计划第六节。
 *
 * @param makespan       最后一件任务完成 tick − 仿真开始 tick
 * @param totalPathLen   全部 AGV 实走弧长之和（含空驶），只统计到最后一个任务完成
 * @param totalWaitTick  处于 WAITING 的（车 × tick）之和，只统计到最后一个任务完成
 * @param conflictCount  预约失败导致车辆真正停住的事件次数；让位不计入
 * @param completedTasks 完成任务数
 * @param avgDeliveryTick 每单从分配到达成的平均 tick；没有已完成任务时为 0
 * @param finished       是否停止推进（全部完成或死锁）
 * @param deadlocked     是否因连续无任务完成而判定死锁
 * @param ticks          仿真总推进 tick
 */
public record SimulationResult(long makespan, double totalPathLen, long totalWaitTick,
                               int conflictCount, int completedTasks, long avgDeliveryTick,
                               boolean finished, boolean deadlocked, long ticks) {
}
