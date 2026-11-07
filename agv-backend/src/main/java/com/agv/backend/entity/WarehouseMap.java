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
 * 车间路网。整张图存为一个 roadnet_json，不按节点/边拆表。
 */
@Entity
@Table(name = "warehouse_map")
public class WarehouseMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Lob
    @Column(name = "roadnet_json", nullable = false)
    private String roadnetJson;

    /** 1 为预置模板，调度人员可另存为副本 */
    @Column(name = "is_template", nullable = false)
    private Boolean isTemplate = false;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoadnetJson() {
        return roadnetJson;
    }

    public void setRoadnetJson(String roadnetJson) {
        this.roadnetJson = roadnetJson;
    }

    public Boolean getIsTemplate() {
        return isTemplate;
    }

    public void setIsTemplate(Boolean template) {
        isTemplate = template;
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
