package com.agv.backend.dto;

/**
 * 车间区域。只用于二维/三维渲染和图例，不参与路径搜索。
 *
 * @param kind 区域类型：CHARGING / PARKING / STORAGE / PICKING / LOADING
 */
public record ZoneDto(String id, String kind, double x, double y,
                      double width, double height, String label) {
}
