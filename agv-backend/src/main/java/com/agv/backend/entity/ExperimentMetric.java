package com.agv.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 实验指标。口径见 specs/毕设实施计划.md 第六节，四个字段与论文实验章一一对应。
 */
@Entity
@Table(name = "experiment_metric")
public class ExperimentMetric extends BaseEntity {

    @Column(name = "experiment_id", nullable = false, unique = true)
    private Long experimentId;

    /** 完成时间：最后一个任务完成的 tick（1 tick = 100ms） */
    @Column(nullable = false)
    private Long makespan;

    /** 总路径长：全部 AGV 实走弧长之和，含空驶（米） */
    @Column(name = "total_path_len", nullable = false)
    private Double totalPathLen;

    /** 总等待：处于 WAITING 的 (车 × tick) 之和，不含无任务空闲 */
    @Column(name = "total_wait_tick", nullable = false)
    private Long totalWaitTick;

    /** 冲突次数：预约失败导致停车的事件次数，一次连续等待记 1 次 */
    @Column(name = "conflict_count", nullable = false)
    private Integer conflictCount;

    public Long getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(Long experimentId) {
        this.experimentId = experimentId;
    }

    public Long getMakespan() {
        return makespan;
    }

    public void setMakespan(Long makespan) {
        this.makespan = makespan;
    }

    public Double getTotalPathLen() {
        return totalPathLen;
    }

    public void setTotalPathLen(Double totalPathLen) {
        this.totalPathLen = totalPathLen;
    }

    public Long getTotalWaitTick() {
        return totalWaitTick;
    }

    public void setTotalWaitTick(Long totalWaitTick) {
        this.totalWaitTick = totalWaitTick;
    }

    public Integer getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(Integer conflictCount) {
        this.conflictCount = conflictCount;
    }
}
