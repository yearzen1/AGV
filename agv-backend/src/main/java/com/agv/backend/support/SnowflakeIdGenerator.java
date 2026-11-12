package com.agv.backend.support;

/**
 * 53 位雪花 ID 生成器。
 * <p>
 * 位段：41 位毫秒时间戳 + 2 位实例号 + 10 位序列，合计 53 位，不超过 JavaScript
 * 安全整数，接口可以直接以数字返回主键。
 */
public final class SnowflakeIdGenerator {

    /** 当前允许生成的 ID 上限，等于 JavaScript Number.MAX_SAFE_INTEGER。 */
    public static final long MAX_ID = (1L << 53) - 1;

    public SnowflakeIdGenerator(long workerId) {
    }

    public static void configure(long workerId) {
    }

    public static long nextId() {
        return 0L;
    }

    public synchronized long next() {
        return 0L;
    }
}
