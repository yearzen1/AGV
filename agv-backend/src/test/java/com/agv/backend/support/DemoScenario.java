package com.agv.backend.support;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.LaneMode;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.Task;
import com.agv.backend.dto.RoadNetPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 论文实验共用的标准车间与 20 单演示任务集，避免各测试各写一份生成规则。 */
public final class DemoScenario {

    public static final long SEED = 20261107L;

    private DemoScenario() {
    }

    public static RoadNetPayload standardPayload() throws Exception {
        return new ObjectMapper().readValue(RoadNetTemplates.STANDARD_WORKSHOP, RoadNetPayload.class);
    }

    public static RoadNet standardNet() throws Exception {
        RoadNetPayload payload = standardPayload();
        return RoadNet.of(
                payload.nodes().stream()
                        .map(n -> new NetNode(n.id(), n.x(), n.y(), NodeType.valueOf(n.type()), n.capacity()))
                        .toList(),
                payload.edges().stream()
                        .map(e -> new NetEdge(e.id(), e.from(), e.to(), e.length(),
                                EdgeDirection.valueOf(e.direction()), e.speed(), e.capacity(),
                                "DUAL".equalsIgnoreCase(e.laneMode()) ? LaneMode.DUAL : LaneMode.SINGLE))
                        .toList());
    }

    /** 20 单在 W1/W2/W3 之间循环，与 DataInitializer 预置任务集一致。 */
    public static List<Task> demoTasks() {
        String[] stations = {"W1", "W2", "W3"};
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String from = stations[i % 3];
            String to = stations[(i + 1 + i / 3) % 3];
            if (from.equals(to)) {
                to = stations[(i + 2) % 3];
            }
            tasks.add(new Task(i + 1, from, to));
        }
        return tasks;
    }
}
