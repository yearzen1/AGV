package com.agv.backend;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.LaneMode;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.AgvSnapshot;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.backend.dto.RoadNetPayload;
import com.agv.backend.support.DemoScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 节点占用诊断：统计「物理停靠在同一节点上的车数超过该节点容量」的 tick 数。
 *
 * <p>不改变引擎行为，只从 {@link SimulationEngine#snapshots()} 逐帧采样；
 * 「在弧上」的车不计入节点占用，与界面节点表口径一致。
 *
 * <p>同时跑一个对照组：只把工位容量从 1 提到 2，其余不变。
 * 若超容 tick 归零，就说明问题主因是「工位没有让位空间」，
 * 而不是等待策略本身的逻辑错误。
 *
 * <p>结果写入 {@code target/node-occupancy.txt}（原模板）与
 * {@code target/node-occupancy-station2.txt}（工位容量 2）。
 */
class NodeOccupancyDiagnosticsTest {

    private static RoadNet toModel(RoadNetPayload payload, int stationCapacity, boolean approachDual) {
        java.util.Set<String> stations = payload.nodes().stream()
                .filter(n -> "WORKSTATION".equals(n.type()))
                .map(n -> n.id())
                .collect(Collectors.toSet());
        return RoadNet.of(
                payload.nodes().stream()
                        .map(n -> new NetNode(n.id(), n.x(), n.y(), NodeType.valueOf(n.type()),
                                stations.contains(n.id()) ? stationCapacity : n.capacity()))
                        .toList(),
                payload.edges().stream()
                        .map(e -> new NetEdge(e.id(), e.from(), e.to(), e.length(),
                                EdgeDirection.valueOf(e.direction()), e.speed(), e.capacity(),
                                "DUAL".equalsIgnoreCase(e.laneMode())
                                        || (approachDual && (stations.contains(e.from()) || stations.contains(e.to())))
                                        ? LaneMode.DUAL : LaneMode.SINGLE))
                        .toList());
    }

    private static String measure(RoadNet net, int agvCount, String algorithm) {
        Map<String, Integer> capacity = net.nodes().values().stream()
                .collect(Collectors.toMap(NetNode::id, NetNode::capacity));
        SimulationEngine engine = new SimulationEngine(net, DemoScenario.demoTasks(),
                SimConfig.defaults(agvCount, DemoScenario.SEED, algorithm));

        long overTicks = 0;
        int maxOver = 0;
        String worstNode = "-";
        String worstStatuses = "-";
        long waitingAtNodeTicks = 0;

        int guard = 0;
        while (!engine.finished() && guard++ < 200_000) {
            engine.step();
            Map<String, List<String>> byNode = new LinkedHashMap<>();
            for (AgvSnapshot snapshot : engine.snapshots()) {
                if (snapshot.arcId() != null || snapshot.nodeId() == null) {
                    continue;
                }
                byNode.computeIfAbsent(snapshot.nodeId(), key -> new ArrayList<>())
                        .add(snapshot.status().name());
            }
            if (byNode.isEmpty()) {
                continue;
            }
            waitingAtNodeTicks += byNode.values().stream()
                    .flatMap(List::stream)
                    .filter(status -> "WAITING".equals(status))
                    .count();
            for (Map.Entry<String, List<String>> entry : byNode.entrySet()) {
                int limit = capacity.getOrDefault(entry.getKey(), 1);
                int actual = entry.getValue().size();
                if (actual <= limit) {
                    continue;
                }
                overTicks++;
                if (actual > maxOver) {
                    maxOver = actual;
                    worstNode = entry.getKey();
                    worstStatuses = String.join(",", entry.getValue());
                }
            }
        }

        SimulationResult result = engine.runToCompletion();
        return String.format("%d\t%s\t%d\t%d\t%s\t%s\t%d\t%d\t%d\t%d\t%d",
                agvCount, algorithm, overTicks, maxOver, worstNode, worstStatuses,
                waitingAtNodeTicks, result.makespan(), result.completedTasks(),
                result.finished() ? 1 : 0, result.deadlocked() ? 1 : 0);
    }

    private static void writeReport(String fileName, int stationCapacity, boolean approachDual) throws Exception {
        RoadNet net = toModel(DemoScenario.standardPayload(), stationCapacity, approachDual);
        List<String> lines = new ArrayList<>();
        lines.add("agv\talgorithm\toverTicks\tmaxOver\tworstNode\tworstStatuses\twaitingAtNodeTicks\tmakespan\tcompleted\tfinished\tdeadlocked");
        for (int count : new int[]{2, 3, 5, 8}) {
            for (String algorithm : new String[]{"nearest", "greedy-eta"}) {
                lines.add(measure(net, count, algorithm));
            }
        }
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", fileName), lines);
    }

    @Test
    @DisplayName("诊断：原模板与容量/车道组合的对照，落盘 target/node-occupancy*.txt")
    void measureNodeOverCapacity() throws Exception {
        writeReport("node-occupancy.txt", 1, false);
        writeReport("node-occupancy-station2.txt", 2, false);
        writeReport("node-occupancy-dual.txt", 1, true);
        writeReport("node-occupancy-dual-station2.txt", 2, true);
    }
}
