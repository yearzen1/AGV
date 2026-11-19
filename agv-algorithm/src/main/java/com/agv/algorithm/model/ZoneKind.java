package com.agv.algorithm.model;

/**
 * 车间区域类型。区域只用于二维/三维渲染和图例，不参与路径搜索、预约与校验。
 */
public enum ZoneKind {
    CHARGING,
    PARKING,
    STORAGE,
    PICKING,
    LOADING
}
