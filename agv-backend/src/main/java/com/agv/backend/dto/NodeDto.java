package com.agv.backend.dto;

/**
 * 前端画布 / JSON 文件共用的节点结构。
 *
 * @param type         PICKING / LOADING / STORAGE / CHARGER / PARKING / BUFFER / JUNCTION
 * @param capacity     节点可同时容纳的 AGV 数，缺省 1
 * @param serviceTicks 该节点装货或卸货耗时，缺省 0
 */
public record NodeDto(String id, double x, double y, String type,
                      Integer capacity, Integer serviceTicks) {

    public NodeDto {
        capacity = capacity == null ? 1 : capacity;
        serviceTicks = serviceTicks == null ? 0 : serviceTicks;
    }

    public NodeDto(String id, double x, double y, String type) {
        this(id, x, y, type, 1, 0);
    }
}
