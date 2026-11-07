package com.agv.algorithm.sim;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.path.AStarPlanner;
import com.agv.algorithm.path.PathResult;

import java.util.List;

/**
 * 贪心 ETA：用图上 A* 估计"空驶去取货 + 取货到送达"的总时间，选预计完成最早的任务。
 * 与就近分配相比会考虑路网绕行与单行道，因此指标通常更好，但单次寻路开销更大。
 */
public final class GreedyEtaAssigner implements TaskAssigner {

    private final RoadNet net;
    private final AStarPlanner planner;

    public GreedyEtaAssigner(RoadNet net, AStarPlanner planner) {
        this.net = net;
        this.planner = planner;
    }

    @Override
    public String name() {
        return "greedy-eta";
    }

    @Override
    public String displayName() {
        return "贪心 ETA";
    }

    @Override
    public Task choose(String currentNodeId, List<Task> candidates) {
        if (candidates.isEmpty() || net.node(currentNodeId) == null) {
            return null;
        }
        Task best = null;
        double bestEta = Double.POSITIVE_INFINITY;
        for (Task task : candidates) {
            if (net.node(task.from()) == null || net.node(task.to()) == null) {
                continue;
            }
            PathResult toPickup = planner.plan(net, currentNodeId, task.from());
            if (!toPickup.found()) {
                continue;
            }
            PathResult delivery = planner.plan(net, task.from(), task.to());
            if (!delivery.found()) {
                continue;
            }
            double eta = toPickup.costSeconds() + delivery.costSeconds();
            if (eta < bestEta - 1e-9) {
                bestEta = eta;
                best = task;
            }
        }
        return best;
    }
}
