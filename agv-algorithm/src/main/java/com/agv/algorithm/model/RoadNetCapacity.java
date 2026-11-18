package com.agv.algorithm.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 加权环容量：判断一张路网最多能容纳多少台 AGV 而不会形成等待环。
 * <p>
 * 定义：对任一有向环 {@code C}，环容量 = 环上所有节点 {@code capacity} 之和；
 * 加权环容量取全部有向环中的最小值。死锁要求环上每个节点都满，因此至少需要
 * 环容量台车才能占满一个环；车队数小于加权环容量时，任何环都占不满，等待图不可能成环。
 * <p>
 * 无环时返回 {@link Integer#MAX_VALUE}：没有环可以占满，车队规模不受拓扑限制。
 */
public final class RoadNetCapacity {

    private RoadNetCapacity() {
    }

    /** 所有有向环中最小的节点容量之和；无环时返回 {@link Integer#MAX_VALUE}。 */
    public static int minCycleCapacity(RoadNet net) {
        if (net == null || net.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        long best = Long.MAX_VALUE;
        for (NetNode start : net.nodes().values()) {
            long cycle = shortestCycleThrough(net, start.id());
            if (cycle < best) {
                best = cycle;
            }
        }
        return best == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, best);
    }

    /** 车队上限 = min(加权环容量 − 1, 充电位容量之和)。 */
    public static int maxAgvCount(RoadNet net) {
        if (net == null || net.isEmpty()) {
            return 0;
        }
        int cycleCapacity = minCycleCapacity(net);
        int structural = cycleCapacity == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(0, cycleCapacity - 1);
        return Math.min(structural, chargerCapacitySum(net));
    }

    /** 所有充电位容量之和：车辆开始和结束都停在这里。 */
    public static int chargerCapacitySum(RoadNet net) {
        if (net == null) {
            return 0;
        }
        int sum = 0;
        for (NetNode node : net.nodes().values()) {
            if (node.type() == NodeType.CHARGER) {
                sum += Math.max(1, node.capacity());
            }
        }
        return sum;
    }

    /**
     * 启动前校验车队规模。超出时抛出带上限值的异常，由调用方转成业务错误，
     * 避免跑到一半才发现站不下。
     */
    public static void requireFleetFits(RoadNet net, int agvCount) {
        int max = maxAgvCount(net);
        if (agvCount > max) {
            throw new IllegalArgumentException(
                    "该路网最多支持 " + max + " 台 AGV（车队上限 = 加权环容量与充电位容量的较小值），请减少车辆数");
        }
    }

    /**
     * 经过 {@code startId} 的最小环容量：以节点容量为代价做 Dijkstra，
     * 再用每一条进入起点的弧闭合回路。
     */
    private static long shortestCycleThrough(RoadNet net, String startId) {
        Map<String, Long> dist = new HashMap<>();
        PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingLong(Step::cost));
        dist.put(startId, 0L);
        queue.add(new Step(startId, 0L));

        while (!queue.isEmpty()) {
            Step current = queue.poll();
            if (current.cost() > dist.getOrDefault(current.nodeId(), Long.MAX_VALUE)) {
                continue;
            }
            for (Arc arc : net.arcsFrom(current.nodeId())) {
                long next = current.cost() + capacityOf(net, arc.to());
                if (next < dist.getOrDefault(arc.to(), Long.MAX_VALUE)) {
                    dist.put(arc.to(), next);
                    queue.add(new Step(arc.to(), next));
                }
            }
        }

        long startCapacity = capacityOf(net, startId);
        long best = Long.MAX_VALUE;
        for (Arc closing : net.arcsTo(startId)) {
            String from = closing.from();
            if (from.equals(startId)) {
                continue;
            }
            Long toFrom = dist.get(from);
            if (toFrom == null) {
                continue;
            }
            long cycle = startCapacity + toFrom;
            if (cycle < best) {
                best = cycle;
            }
        }
        return best;
    }

    private static long capacityOf(RoadNet net, String nodeId) {
        NetNode node = net.node(nodeId);
        return node == null ? 1L : Math.max(1, node.capacity());
    }

    private record Step(String nodeId, long cost) {
    }
}
