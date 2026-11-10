package com.agv.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 离线对比实验。algorithm 为空时对比全部算法。
 */
public record ExperimentRequest(
        @NotNull(message = "请选择路网") Long roadnetId,
        @NotNull(message = "请选择任务集") Long taskSetId,
        @Pattern(regexp = "nearest|greedy-eta", message = "调度算法只能是 nearest 或 greedy-eta")
        String algorithm,
        @Min(value = 1, message = "AGV 数量至少为 1")
        @Max(value = 50, message = "AGV 数量最多为 50")
        Integer agvCount,
        Long seed) {
}
