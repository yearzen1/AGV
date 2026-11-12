package com.agv.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 主键结构约束：数字类型、由应用侧生成，数据库不能自增。
 */
@SpringBootTest
class PrimaryKeySchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("7 张表的主键都是 BIGINT 且不使用自增")
    void primaryKeysAreApplicationAssigned() {
        Integer idColumns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and column_name = 'id'
                """, Integer.class);
        assertEquals(7, idColumns, "应存在 7 个 id 主键列");

        Integer identityColumns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and column_name = 'id' and is_identity = 'YES'
                """, Integer.class);
        assertEquals(0, identityColumns, "主键不能使用数据库自增");

        Integer bigintColumns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and column_name = 'id' and upper(data_type) = 'BIGINT'
                """, Integer.class);
        assertEquals(7, bigintColumns, "主键必须是 BIGINT 数字类型");
    }
}
