package com.agv.backend.dto;

/**
 * 路段结构。direction 取 ONE_WAY / TWO_WAY，laneMode 取 SINGLE / DUAL。
 */
public record EdgeDto(String id, String from, String to, double length,
                      String direction, double speed, int capacity, String laneMode) {

    public EdgeDto {
        laneMode = "DUAL".equalsIgnoreCase(laneMode) ? "DUAL" : "SINGLE";
    }

    /** 兼容旧 JSON：缺省单车道。 */
    public EdgeDto(String id, String from, String to, double length,
                   String direction, double speed, int capacity) {
        this(id, from, to, length, direction, speed, capacity, "SINGLE");
    }
}
