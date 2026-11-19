package com.agv.backend.dto;

import com.agv.algorithm.sim.TaskOperation;

/**
 * 任务集里的单条两点搬运任务。
 *
 * @param operation LOAD 装货 / UNLOAD 卸货，缺省 LOAD
 */
public record TaskDto(Integer id, String from, String to, TaskOperation operation) {

    public TaskDto {
        operation = operation == null ? TaskOperation.LOAD : operation;
    }

    public TaskDto(Integer id, String from, String to) {
        this(id, from, to, TaskOperation.LOAD);
    }
}
