package com.agv.algorithm.sim;

import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.RoadNet;

import java.util.List;

/**
 * 就近分配：把任务给欧氏距离最近的空闲 AGV。不预判路网绕行，因此容易"看起来近、实际远"。
 */
public final class NearestAssigner implements TaskAssigner {

    private final RoadNet net;

    public NearestAssigner(RoadNet net) {
        this.net = net;
    }

    @Override
    public String name() {
        return "nearest";
    }

    @Override
    public String displayName() {
        return "就近分配";
    }

    @Override
    public Task choose(String currentNodeId, List<Task> candidates) {
        NetNode current = net.node(currentNodeId);
        if (current == null || candidates.isEmpty()) {
            return null;
        }
        Task best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Task task : candidates) {
            NetNode pickup = net.node(task.from());
            if (pickup == null) {
                continue;
            }
            double distance = current.distanceTo(pickup);
            if (distance < bestDistance - 1e-9) {
                bestDistance = distance;
                best = task;
            }
        }
        return best;
    }
}
