package com.agv.algorithm.sim;

/**
 * 某一帧内单台 AGV 的快照。前端用 {@code (arcId, s)} 与弧端点插值出坐标。
 *
 * @param id       AGV 编号，如 AGV-1
 * @param status   运行状态
 * @param arcId    当前所在弧；在节点上时为 null
 * @param s        在弧上已行驶的米数
 * @param x        插值坐标 X
 * @param y        插值坐标 Y
 * @param heading  朝向（度）
 * @param taskId   当前任务号；无任务为 -1
 * @param waitTicks 已连续等待的 tick 数
 * @param nodeId   当前所在节点；在弧上时为出发节点
 */
public record AgvSnapshot(String id, AgvStatus status, String arcId, double s,
                          double x, double y, double heading, int taskId, int waitTicks,
                          String nodeId) {
}
