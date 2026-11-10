package com.agv.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 启动实时仿真。
 *
 * @param seed 为空时随机生成一个并入库，保证可复现
 */
public record StartSimRequest(
        @NotNull(message = "请选择路网") Long roadnetId,
        @NotNull(message = "请选择任务集") Long taskSetId,
        @NotNull(message = "请选择调度算法") String algorithm,
        @Min(value = 1, message = "AGV 数量至少为 1") Integer agvCount,
        Long seed) {
}
