package com.agv.algorithm.model;

/**
 * 车间布局区域：矩形，只做渲染与图例。
 *
 * @param id     区域编号
 * @param kind   区域类型
 * @param x      左上角 X（米）
 * @param y      左上角 Y（米）
 * @param width  宽度（米），必须大于 0
 * @param height 高度（米），必须大于 0
 * @param label  显示名称，可为空
 */
public record Zone(String id, ZoneKind kind, double x, double y,
                   double width, double height, String label) {
}
