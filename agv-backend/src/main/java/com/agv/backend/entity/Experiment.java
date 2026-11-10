package com.agv.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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

    @Column(name = "roadnet_id", nullable = false)
    private Long roadnetId;

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

    /** 创建实验时的整张路网 JSON。旧实验为空，回放时拒绝。 */
    @Lob
    @Column(name = "roadnet_snapshot")
    private String roadnetSnapshot;

    /** 创建实验时的任务集 JSON。旧实验为空，回放时拒绝。 */
    @Lob
    @Column(name = "taskset_snapshot")
    private String tasksetSnapshot;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoadnetId() {
        return roadnetId;
    }

    public void setRoadnetId(Long roadnetId) {
        this.roadnetId = roadnetId;
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

    public String getRoadnetSnapshot() {
        return roadnetSnapshot;
    }

    public void setRoadnetSnapshot(String roadnetSnapshot) {
        this.roadnetSnapshot = roadnetSnapshot;
    }

    public String getTasksetSnapshot() {
        return tasksetSnapshot;
    }

    public void setTasksetSnapshot(String tasksetSnapshot) {
        this.tasksetSnapshot = tasksetSnapshot;
    }
}
