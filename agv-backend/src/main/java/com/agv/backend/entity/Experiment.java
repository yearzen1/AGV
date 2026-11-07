package com.agv.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 一次仿真实验。同一 map + task_set 换 algorithm 重跑，即构成对比实验。
 */
@Entity
@Table(name = "experiment")
public class Experiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_id", nullable = false)
    private Long mapId;

    @Column(name = "task_set_id", nullable = false)
    private Long taskSetId;

    /** 调度算法字典码：nearest / greedy-eta */
    @Column(nullable = false, length = 32)
    private String algorithm;

    @Column(name = "agv_count", nullable = false)
    private Integer agvCount;

    /** 随机种子，保证可复现 */
    @Column(nullable = false)
    private Long seed;

    /** CREATED / RUNNING / FINISHED / FAILED */
    @Column(nullable = false, length = 16)
    private String status = "CREATED";

    @Column(name = "create_user")
    private Long createUser;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMapId() {
        return mapId;
    }

    public void setMapId(Long mapId) {
        this.mapId = mapId;
    }

    public Long getTaskSetId() {
        return taskSetId;
    }

    public void setTaskSetId(Long taskSetId) {
        this.taskSetId = taskSetId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Integer getAgvCount() {
        return agvCount;
    }

    public void setAgvCount(Integer agvCount) {
        this.agvCount = agvCount;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreateUser() {
        return createUser;
    }

    public void setCreateUser(Long createUser) {
        this.createUser = createUser;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
