package com.agv.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveTaskSetRequest(
        @NotBlank(message = "任务集名称不能为空") String name,
        @NotNull(message = "必须选择路网") Long mapId,
        @NotNull(message = "任务列表不能为空") TaskSetPayload taskSet) {
}
