package com.agv.algorithm.sim;

/**
 * 离线跑完一轮实验的结果。口径见实施计划第六节。
 *
 * @param makespan       最后一件任务完成 tick − 仿真开始 tick
 * @param totalPathLen   全部 AGV 实走弧长之和（含空驶）
 * @param totalWaitTick  处于 WAITING 的（车 × tick）之和
 * @param conflictCount  预约失败导致停车的事件次数
 * @param completedTasks 完成任务数
 * @param finished       是否全部任务完成
 * @param ticks          仿真总推进 tick
 */
public record SimulationResult(long makespan, double totalPathLen, long totalWaitTick,
                               int conflictCount, int completedTasks, boolean finished, long ticks) {
}
