package com.agv.backend.dto;

import java.util.List;

/**
 * 任务集 JSON 根对象，前后端共用。
 */
public record TaskSetPayload(List<TaskDto> tasks) {

    public TaskSetPayload {
        tasks = tasks == null ? List.of() : tasks;
    }
}
