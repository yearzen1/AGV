package com.agv.backend.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("连续生成的 ID 唯一、为正且不超过 JS 安全整数")
    void generatesUniqueSafeIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0);
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            long id = generator.next();
            assertTrue(id > 0, "ID 必须为正数");
            assertTrue(id <= SnowflakeIdGenerator.MAX_ID, "ID 不能超过 JS 安全整数");
            assertTrue(ids.add(id), "ID 不能重复");
        }

        assertEquals(10_000, ids.size());
    }

    @Test
    @DisplayName("同一实例内 ID 单调递增")
    void generatesMonotonicIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        long previous = generator.next();

        for (int i = 0; i < 1_000; i++) {
            long current = generator.next();
            assertTrue(current > previous, "同一实例内后生成的 ID 必须更大");
            previous = current;
        }
    }

    @Test
    @DisplayName("实例号超出 0～3 时拒绝构造")
    void rejectsWorkerIdOutsideRange() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(4));
    }

    @Test
    @DisplayName("ID 上限不超过 JavaScript 安全整数")
    void maxIdFitsJavaScriptSafeInteger() {
        assertTrue(SnowflakeIdGenerator.MAX_ID <= 9_007_199_254_740_991L);
    }
}
