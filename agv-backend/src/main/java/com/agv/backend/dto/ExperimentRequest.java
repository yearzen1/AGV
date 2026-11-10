package com.agv.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 离线对比实验。algorithm 为空时对比全部算法。
 */
public record ExperimentRequest(
        @NotNull(message = "请选择路网") Long roadnetId,
        @NotNull(message = "请选择任务集") Long taskSetId,
        String algorithm,
        @Min(value = 1, message = "AGV 数量至少为 1") Integer agvCount,
        Long seed) {
}
