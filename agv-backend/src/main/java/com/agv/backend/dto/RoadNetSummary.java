package com.agv.backend.dto;

import java.time.LocalDateTime;

/**
 * 路网列表项，只带概要，不返回整张图。
 */
public record RoadNetSummary(Long id, String name, Boolean isTemplate, Long createUser,
                             LocalDateTime createTime, int nodeCount, int edgeCount) {
}
