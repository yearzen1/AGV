package com.agv.backend.entity;

import com.agv.backend.support.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

/**
 * 所有持久化实体的公共主键。
 * <p>
 * 主键为 {@code BIGINT} 数字类型，由应用侧雪花算法在首次持久化前赋值，
 * 数据库建表脚本不写 {@code AUTO_INCREMENT}。
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @PrePersist
    protected void assignId() {
        if (id == null) {
            id = SnowflakeIdGenerator.nextId();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
