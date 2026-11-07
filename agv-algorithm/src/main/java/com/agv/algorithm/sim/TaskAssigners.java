package com.agv.algorithm.sim;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.path.AStarPlanner;

/**
 * 调度算法工厂。字典里存的是 code，这里映射到实现。
 */
public final class TaskAssigners {

    private TaskAssigners() {
    }

    public static TaskAssigner of(String code, RoadNet net, AStarPlanner planner) {
        return switch (code == null ? "" : code) {
            case "nearest" -> new NearestAssigner(net);
            case "greedy-eta" -> new GreedyEtaAssigner(net, planner);
            default -> throw new IllegalArgumentException("未知调度算法：" + code);
        };
    }
}
