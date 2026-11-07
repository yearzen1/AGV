package com.agv.backend.dto;

/**
 * 路段结构。direction 取 ONE_WAY / TWO_WAY。
 */
public record EdgeDto(String id, String from, String to, double length,
                      String direction, double speed, int capacity) {
}
