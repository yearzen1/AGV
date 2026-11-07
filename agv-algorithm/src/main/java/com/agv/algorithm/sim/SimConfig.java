package com.agv.algorithm.sim;

/**
 * 仿真参数。默认值与实施计划一致：100ms/tick、每单 0.5s 装卸、等待 3s 触发重规划。
 *
 * @param agvCount         AGV 数量
 * @param seed             随机种子（用于等待退避抖动），同种子结果可复现
 * @param algorithm        调度算法字典码：nearest / greedy-eta
 * @param serviceTicks     每次装卸货耗时（tick）
 * @param waitTimeoutTicks 连续等待超过该值触发带禁行约束的重规划
 * @param maxTicks         最大推进 tick，防止死循环
 */
public record SimConfig(int agvCount, long seed, String algorithm, int serviceTicks,
                        int waitTimeoutTicks, long maxTicks) {

    public static final long TICK_MILLIS = 100L;

    public static SimConfig defaults(int agvCount, long seed, String algorithm) {
        return new SimConfig(agvCount, seed, algorithm, 5, 30, 200_000);
    }

    public double deltaSeconds() {
        return TICK_MILLIS / 1000.0;
    }
}
