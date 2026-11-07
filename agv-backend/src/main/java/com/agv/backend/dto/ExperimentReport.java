package com.agv.backend.dto;

import java.time.LocalDateTime;

/**
 * 一次实验的指标报表行。四个指标口径见实施计划第六节。
 */
public record ExperimentReport(Long id, Long mapId, Long taskSetId, String algorithm,
                               String algorithmName, Integer agvCount, Long seed,
                               String status, Long makespan, Double totalPathLen,
                               Long totalWaitTick, Integer conflictCount,
                               LocalDateTime createTime) {
}
