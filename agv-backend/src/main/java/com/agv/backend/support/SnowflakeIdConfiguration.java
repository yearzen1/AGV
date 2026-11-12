package com.agv.backend.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 从配置读取当前实例号并初始化雪花生成器。
 */
@Configuration
public class SnowflakeIdConfiguration {

    public SnowflakeIdConfiguration(@Value("${agv.id.worker-id:0}") long workerId) {
        SnowflakeIdGenerator.configure(workerId);
    }
}
