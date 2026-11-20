package com.agv.backend.dto;

import java.util.List;

/**
 * 路网 JSON 根对象，前后端共用（见 specs/毕设实施计划.md 第四节）。
 * <p>
 * {@code zones} 是地图上的区域注释（充电区、进货区等），只用于显示和导出；
 * {@code RoadNet}、校验器和仿真引擎都不读取它。
 */
public record RoadNetPayload(List<NodeDto> nodes, List<EdgeDto> edges, Integer width, Integer height,
                             List<ZoneDto> zones) {

    public static final int DEFAULT_WIDTH = 50;
    public static final int DEFAULT_HEIGHT = 34;

    public RoadNetPayload {
        nodes = nodes == null ? List.of() : nodes;
        edges = edges == null ? List.of() : edges;
        width = width == null ? DEFAULT_WIDTH : width;
        height = height == null ? DEFAULT_HEIGHT : height;
        zones = zones == null ? List.of() : zones;
    }

    /** 兼容只包含 nodes / edges / width / height 的旧 JSON 和旧调用。 */
    public RoadNetPayload(List<NodeDto> nodes, List<EdgeDto> edges, Integer width, Integer height) {
        this(nodes, edges, width, height, List.of());
    }

    /** 兼容只包含 nodes / edges 的旧 JSON。 */
    public RoadNetPayload(List<NodeDto> nodes, List<EdgeDto> edges) {
        this(nodes, edges, DEFAULT_WIDTH, DEFAULT_HEIGHT, List.of());
    }
}
