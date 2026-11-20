package com.agv.algorithm.sim;

/**
 * 一次仿真运行的诊断计数，只用于后端摘要日志，不进入 WebSocket 帧，也不落库。
 *
 * @param replanCount         成功绕行重规划次数
 * @param deadlockBreakCount  检测到等待环的次数
 * @param retreatSuccessCount 倒车让行成功次数
 * @param maxBlockedTicks    单次完整阻塞段的最长 tick（跨重规划累计）
 * @param graceFinished       是否因回充宽限期到期而结束
 * @param unparkedAtFinish    结束时未处于 CHARGING / DONE 的车辆数
 * @param coLocationEvents    同点重合开始/变化/结束事件数
 */
public record SimDiagnostics(int replanCount, int deadlockBreakCount, int retreatSuccessCount,
                             int maxBlockedTicks,
                             boolean graceFinished, int unparkedAtFinish, int coLocationEvents) {
}
