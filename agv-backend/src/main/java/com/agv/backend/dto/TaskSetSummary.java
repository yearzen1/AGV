package com.agv.backend.dto;

import java.time.LocalDateTime;

public record TaskSetSummary(Long id, String name, int taskCount, Long createUser,
                             LocalDateTime createTime) {
}
