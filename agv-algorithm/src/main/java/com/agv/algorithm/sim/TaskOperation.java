package com.agv.algorithm.sim;

/**
 * 任务作业类型。
 * <p>
 * 两点搬运任务始终在起点装货、在终点卸货；作业类型用于标注业务方向并约束端点：
 * <ul>
 *     <li>{@code LOAD}：拣货位/存储位 → 上货位/存储位；</li>
 *     <li>{@code UNLOAD}：上货位/存储位 → 拣货位/存储位。</li>
 * </ul>
 */
public enum TaskOperation {
    LOAD,
    UNLOAD
}
