package com.agv.backend.support;

/**
 * 53 位雪花 ID 生成器。
 * <p>
 * 位段：41 位毫秒时间戳 + 2 位实例号 + 10 位序列，合计 53 位，不超过 JavaScript
 * 安全整数，接口可以直接以数字返回主键。数据库主键列不再使用自增。
 */
public final class SnowflakeIdGenerator {

    /** 当前允许生成的 ID 上限，等于 JavaScript Number.MAX_SAFE_INTEGER。 */
    public static final long MAX_ID = (1L << 53) - 1;

    /** 时间戳起点：2024-01-01T00:00:00Z。 */
    private static final long EPOCH = 1_704_067_200_000L;

    private static final long WORKER_BITS = 2L;
    private static final long SEQUENCE_BITS = 10L;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = WORKER_BITS + SEQUENCE_BITS;
    private static final long MAX_ELAPSED = 1L << 41;

    /** Spring 启动时由 {@link SnowflakeIdConfiguration} 替换为配置的实例号。 */
    private static SnowflakeIdGenerator instance = new SnowflakeIdGenerator(0L);

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 0 到 " + MAX_WORKER_ID + " 之间");
        }
        this.workerId = workerId;
    }

    /** 设置当前进程使用的实例号；只应在应用启动时调用一次。 */
    public static synchronized void configure(long workerId) {
        instance = new SnowflakeIdGenerator(workerId);
    }

    /** 供实体在 {@code @PrePersist} 时调用。 */
    public static synchronized long nextId() {
        return instance.next();
    }

    public synchronized long next() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("检测到系统时钟回拨，拒绝生成雪花 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        long elapsed = timestamp - EPOCH;
        if (elapsed < 0 || elapsed >= MAX_ELAPSED) {
            throw new IllegalStateException("系统时间超出雪花 ID 可用范围");
        }
        lastTimestamp = timestamp;
        return (elapsed << TIMESTAMP_SHIFT) | (workerId << WORKER_SHIFT) | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
