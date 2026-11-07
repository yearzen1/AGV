package com.agv.algorithm.path;

import com.agv.algorithm.model.Arc;

import java.util.List;

/**
 * 路径规划结果。
 *
 * @param arcs          从起点到终点的有向弧序列；未找到时为空列表
 * @param costSeconds   总代价（秒），含路段行驶时间与转弯惩罚
 * @param totalLength   总里程（米），不含转弯惩罚
 * @param expandedStates 扩展的状态数，用于对比算法效率
 * @param found         是否找到路径
 */
public record PathResult(List<Arc> arcs, double costSeconds, double totalLength,
                         int expandedStates, boolean found) {

    public PathResult {
        arcs = List.copyOf(arcs);
    }

    public static PathResult notFound(int expandedStates) {
        return new PathResult(List.of(), Double.POSITIVE_INFINITY, 0, expandedStates, false);
    }

    /** 路径经过的节点 ID 序列（含起点与终点）。 */
    public List<String> nodeIds() {
        if (arcs.isEmpty()) {
            return List.of();
        }
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(arcs.get(0).from()),
                        arcs.stream().map(Arc::to))
                .toList();
    }
}
