package com.agv.algorithm.sim;

/**
 * 搬运任务：从 from 节点取货，送到 to 节点。
 *
 * @param id   任务编号，决定分配顺序（升序）
 * @param from 起点节点 ID
 * @param to   终点节点 ID
 */
public record Task(int id, String from, String to) {
}
