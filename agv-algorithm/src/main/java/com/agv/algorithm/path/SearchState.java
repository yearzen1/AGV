package com.agv.algorithm.path;

/**
 * A* 搜索状态。
 * <p>
 * 代价中包含转弯惩罚，因此 g 值依赖"从哪条弧来"，只按 nodeId 关闭会产生次优解。
 *
 * @param nodeId  当前所在节点
 * @param inArcId 驶入当前节点的弧 ID；起点为 null
 */
public record SearchState(String nodeId, String inArcId) {
}
