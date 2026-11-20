package com.agv.backend.dto;

/** 历史实验的一条过程事件。 */
public record ExperimentEventView(int seq, long tick, String agvId, String type,
                                  String place, String detail) {
}
