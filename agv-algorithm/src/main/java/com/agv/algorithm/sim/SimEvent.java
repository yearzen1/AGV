package com.agv.algorithm.sim;

/**
 * 仿真事件。只推前端日志面板，不落库（实施计划第六节）。
 *
 * @param tick   发生时刻
 * @param agvId  相关 AGV
 * @param type   事件类型，见 {@link Type}
 * @param place  相关弧 ID 或节点 ID
 * @param detail 说明文本
 */
public record SimEvent(long tick, String agvId, Type type, String place, String detail) {

    public enum Type {
        /** 任务分配 */
        TASK_ASSIGNED,
        /** 到达取货点开始装货 */
        LOADING,
        /** 任务完成 */
        TASK_DONE,
        /** 预约失败开始等待（一次连续等待只记一次） */
        WAIT_CONFLICT,
        /** 超时重规划 */
        REPLAN,
        /** 检测到等待环并强制打破 */
        DEADLOCK_BREAK,
        /** 回充完成 */
        CHARGING,
        /** 全部任务完成 */
        FINISHED
    }
}
