package com.agv.backend.dto;

/**
 * 前端画布 / JSON 文件共用的节点结构。
 *
 * @param type WORKSTATION / CHARGER / BUFFER / JUNCTION
 */
public record NodeDto(String id, double x, double y, String type) {
}
