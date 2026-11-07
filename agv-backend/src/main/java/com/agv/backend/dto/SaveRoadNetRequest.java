package com.agv.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveRoadNetRequest(
        @NotBlank(message = "路网名称不能为空") String name,
        @NotNull(message = "路网数据不能为空") RoadNetPayload roadnet,
        Boolean isTemplate) {
}
