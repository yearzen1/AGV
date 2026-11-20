package com.agv.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 地图区域注释。只用于显示和导出，不进入 RoadNet、校验器和仿真引擎。
 *
 * @param id     图内唯一编号，如 Z1
 * @param name   显示名
 * @param type   CHARGING / RECEIVING / UNLOADING / RACK / PARKING / OTHER
 * @param x      左上角 X，单位米
 * @param y      左上角 Y，单位米
 * @param width  宽度，单位米
 * @param height 高度，单位米
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZoneDto(String id, String name, String type,
                      Double x, Double y, Double width, Double height) {
}
