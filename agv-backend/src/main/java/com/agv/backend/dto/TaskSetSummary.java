package com.agv.backend.dto;

import java.time.LocalDateTime;

public record TaskSetSummary(Long id, String name, Long mapId, String mapName, int taskCount,
                             Long createUser, LocalDateTime createTime) {
}
