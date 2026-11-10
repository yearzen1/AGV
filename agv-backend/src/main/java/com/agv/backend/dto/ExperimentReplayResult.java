package com.agv.backend.dto;

/**
 * 一次回放的结果：原实验、新实验，以及四项指标是否一致。
 */
public record ExperimentReplayResult(Long experimentId, Long replayId, boolean consistent,
                                     ExperimentReport original, ExperimentReport replay) {
}
