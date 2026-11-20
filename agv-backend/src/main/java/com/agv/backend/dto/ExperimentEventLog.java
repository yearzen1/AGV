package com.agv.backend.dto;

import java.util.List;

/** 实验事件查询结果；超过上限时 truncated=true。 */
public record ExperimentEventLog(List<ExperimentEventView> events, boolean truncated) {
}
