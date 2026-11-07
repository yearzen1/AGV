package com.agv.backend.dto;

/**
 * 任务集里的单条搬运任务。
 */
public record TaskDto(Integer id, String from, String to) {
}
