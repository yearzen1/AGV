package com.agv.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 实验过程事件。按实验、按顺序保存，单实验有上限；逐帧位置不落库。
 */
@Entity
@Table(name = "simulation_event")
public class SimulationEventEntity extends BaseEntity {

    @Column(name = "experiment_id", nullable = false)
    private Long experimentId;

    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    @Column(nullable = false)
    private Long tick;

    @Column(name = "agv_id", length = 32)
    private String agvId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 64)
    private String place;

    @Column(length = 500)
    private String detail;

    public Long getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(Long experimentId) {
        this.experimentId = experimentId;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public Long getTick() {
        return tick;
    }

    public void setTick(Long tick) {
        this.tick = tick;
    }

    public String getAgvId() {
        return agvId;
    }

    public void setAgvId(String agvId) {
        this.agvId = agvId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
