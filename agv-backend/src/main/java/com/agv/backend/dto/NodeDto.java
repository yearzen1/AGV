package com.agv.backend.dto;

/**
 * 前端画布 / JSON 文件共用的节点结构。
 *
 * @param type     WORKSTATION / CHARGER / BUFFER / JUNCTION
 * @param capacity 节点可同时容纳的 AGV 数，缺省 1
 */
public record NodeDto(String id, double x, double y, String type, Integer capacity) {

    public NodeDto {
        capacity = capacity == null ? 1 : capacity;
    }

    public NodeDto(String id, double x, double y, String type) {
        this(id, x, y, type, 1);
    }
}
