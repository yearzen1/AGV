package com.agv.backend.dto;

import java.util.List;

/**
 * 路网 JSON 根对象，前后端共用（见 specs/毕设实施计划.md 第四节）。
 */
public record RoadNetPayload(List<NodeDto> nodes, List<EdgeDto> edges) {

    public RoadNetPayload {
        nodes = nodes == null ? List.of() : nodes;
        edges = edges == null ? List.of() : edges;
    }
}
