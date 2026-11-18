package com.agv.algorithm.sim;

/**
 * 仿真参数。默认值与实施计划一致：100ms/tick、每单 0.5s 装卸。
 *
 * @param agvCount     AGV 数量，不能超过路网的车队上限
 * @param seed         实验记录字段；引擎不含随机源，同一组输入必得同一结果
 * @param algorithm    调度算法字典码：nearest / greedy-eta
 * @param serviceTicks 每次装卸货耗时（tick）
 * @param maxTicks     最大推进 tick，防止死循环
 */
public record SimConfig(int agvCount, long seed, String algorithm, int serviceTicks,
                        long maxTicks) {

    public static final long TICK_MILLIS = 100L;

    public static SimConfig defaults(int agvCount, long seed, String algorithm) {
        return new SimConfig(agvCount, seed, algorithm, 5, 200_000);
    }

    public double deltaSeconds() {
        return TICK_MILLIS / 1000.0;
    }
}
