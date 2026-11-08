package com.agv.backend.dto;

import java.util.List;

/**
 * 路网 JSON 根对象，前后端共用（见 specs/毕设实施计划.md 第四节）。
 */
public record RoadNetPayload(List<NodeDto> nodes, List<EdgeDto> edges, Integer width, Integer height) {

    public static final int DEFAULT_WIDTH = 50;
    public static final int DEFAULT_HEIGHT = 34;

    public RoadNetPayload {
        nodes = nodes == null ? List.of() : nodes;
        edges = edges == null ? List.of() : edges;
        width = width == null ? DEFAULT_WIDTH : width;
        height = height == null ? DEFAULT_HEIGHT : height;
    }

    /** 兼容只包含 nodes / edges 的旧 JSON。 */
    public RoadNetPayload(List<NodeDto> nodes, List<EdgeDto> edges) {
        this(nodes, edges, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
}
