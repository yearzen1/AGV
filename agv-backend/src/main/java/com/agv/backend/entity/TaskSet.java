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
 * 任务集。tasks_json 是搬运任务列表，每个任务含起点与终点节点 ID。
 */
@Entity
@Table(name = "task_set")
public class TaskSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    /** 绑定的路网。节点编号只在单张路网内唯一，任务集不能跨图复用。 */
    @Column(name = "map_id")
    private Long roadnetId;

    @Lob
    @Column(name = "tasks_json", nullable = false)
    private String tasksJson;

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

    public Long getRoadnetId() {
        return roadnetId;
    }

    public void setRoadnetId(Long roadnetId) {
        this.roadnetId = roadnetId;
    }

    public String getTasksJson() {
        return tasksJson;
    }

    public void setTasksJson(String tasksJson) {
        this.tasksJson = tasksJson;
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
