package com.agv.backend.support;

import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.backend.common.BusinessException;

/**
 * 当前仿真规则版本与启动前约束。规则版本写入实验，回放时用于判断指标是否可直接比较。
 */
public final class SimulationRules {

    /** P2 停线等待与死锁语义。修改仿真规则时递增。 */
    public static final String ENGINE_VERSION = "2";

    private SimulationRules() {
    }

    /** 路网可容纳的 AGV 上限 = 全部充电位容量之和。 */
    public static int maxAgvCount(RoadNet net) {
        int total = 0;
        for (NetNode node : net.nodes().values()) {
            if (node.type() == NodeType.CHARGER) {
                total += Math.max(1, node.capacity());
            }
        }
        return total;
    }

    public static void requireAgvCapacity(RoadNet net, int agvCount) {
        int max = maxAgvCount(net);
        if (agvCount > max) {
            throw new BusinessException(4004,
                    "AGV 数量超过充电位总容量（最多 " + max + " 台）");
        }
    }
}
