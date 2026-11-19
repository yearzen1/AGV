package com.agv.algorithm.sim;

/**
 * 两点搬运任务：从 from 装货，送到 to 卸货。
 *
 * @param id        任务编号，决定分配顺序（升序）
 * @param from      起点节点 ID
 * @param to        终点节点 ID
 * @param operation 作业类型，缺省 LOAD
 */
public record Task(int id, String from, String to, TaskOperation operation) {

    public Task(int id, String from, String to) {
        this(id, from, to, TaskOperation.LOAD);
    }

    public Task {
        operation = operation == null ? TaskOperation.LOAD : operation;
    }
}
