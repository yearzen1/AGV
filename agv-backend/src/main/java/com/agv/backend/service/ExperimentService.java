package com.agv.backend.service;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.algorithm.sim.Task;
import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.ExperimentReplayResult;
import com.agv.backend.dto.ExperimentReport;
import com.agv.backend.dto.ExperimentRequest;
import com.agv.backend.dto.RoadNetPayload;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.entity.Experiment;
import com.agv.backend.entity.ExperimentMetric;
import com.agv.backend.entity.SysUser;
import com.agv.backend.entity.TaskSet;
import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.security.CurrentUser;
import com.agv.backend.support.TaskSetValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 离线对比实验：同一路网、同一任务集、同一 seed 换算法重跑，指标入库。
 * <p>
 * 每次实验都会保存输入快照；回放直接用快照重跑，不依赖当前路网。
 */
@Service
public class ExperimentService {

    private static final Map<String, String> ALGORITHM_NAMES = Map.of(
            "nearest", "就近分配",
            "greedy-eta", "贪心 ETA");

    private final RoadNetService roadNetService;
    private final TaskSetService taskSetService;
    private final TaskSetValidator taskSetValidator;
    private final ExperimentRepository experimentRepository;
    private final ExperimentMetricRepository metricRepository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public ExperimentService(RoadNetService roadNetService, TaskSetService taskSetService,
                             TaskSetValidator taskSetValidator, ExperimentRepository experimentRepository,
                             ExperimentMetricRepository metricRepository, CurrentUser currentUser,
                             ObjectMapper objectMapper) {
        this.roadNetService = roadNetService;
        this.taskSetService = taskSetService;
        this.taskSetValidator = taskSetValidator;
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    /** 跑一组实验；algorithm 为空时对全部算法各跑一次，用于对比报表。 */
    public List<ExperimentReport> run(ExperimentRequest request) {
        List<String> algorithms = request.algorithm() == null || request.algorithm().isBlank()
                ? List.of("nearest", "greedy-eta")
                : List.of(request.algorithm());

        TaskSet taskSet = taskSetService.require(request.taskSetId());
        if (taskSet.getRoadnetId() == null) {
            throw new BusinessException("任务集「" + taskSet.getName() + "」未绑定路网，请先编辑");
        }
        if (!taskSet.getRoadnetId().equals(request.roadnetId())) {
            throw new BusinessException("任务集「" + taskSet.getName() + "」绑定的路网与所选路网不一致");
        }

        RoadNet net = roadNetService.toModel(roadNetService.detail(request.roadnetId()));
        TaskSetPayload payload = taskSetService.detail(request.taskSetId());
        taskSetValidator.validateOrThrow(net, payload.tasks());
        List<Task> tasks = toTasks(payload);
        String roadnetJson = roadNetService.rawJson(request.roadnetId());
        String tasksetJson = taskSetService.rawJson(request.taskSetId());
        int agvCount = request.agvCount() == null ? 3 : request.agvCount();
        long seed = request.seed() == null ? 20261107L : request.seed();

        List<ExperimentReport> reports = new ArrayList<>();
        for (String algorithm : algorithms) {
            reports.add(runOne(net, tasks, algorithm, agvCount, seed, request, roadnetJson, tasksetJson));
        }
        return reports;
    }

    private ExperimentReport runOne(RoadNet net, List<Task> tasks, String algorithm,
                                    int agvCount, long seed, ExperimentRequest request,
                                    String roadnetJson, String tasksetJson) {
        SimulationResult result;
        try {
            SimulationEngine engine = new SimulationEngine(net, tasks,
                    SimConfig.defaults(agvCount, seed, algorithm));
            result = engine.runToCompletion();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(4004, "实验无法运行：" + ex.getMessage());
        }

        Experiment experiment = new Experiment();
        experiment.setRoadnetId(request.roadnetId());
        experiment.setTaskSetId(request.taskSetId());
        experiment.setAlgorithm(algorithm);
        experiment.setAgvCount(agvCount);
        experiment.setSeed(seed);
        experiment.setStatus(result.finished() ? "FINISHED" : "FAILED");
        experiment.setCreateUser(currentUser.id());
        experiment.setRoadnetSnapshot(roadnetJson);
        experiment.setTasksetSnapshot(tasksetJson);
        Experiment saved = experimentRepository.save(experiment);

        ExperimentMetric metric = saveMetric(saved.getId(), result);
        return toReport(saved, Optional.of(metric));
    }

    /**
     * 用实验输入快照重跑一次，并比较四项指标。
     * 回放会新增一条实验记录，原记录保持不变。
     */
    @Transactional
    public ExperimentReplayResult replay(Long id) {
        Experiment original = experimentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(4040, "实验不存在：" + id));
        requireReplayable(original);
        ExperimentMetric originalMetric = metricRepository.findByExperimentId(id)
                .orElseThrow(() -> new BusinessException(4006, "该实验没有指标记录，无法回放"));

        RoadNetPayload roadnet = readRoadnet(original.getRoadnetSnapshot());
        TaskSetPayload taskSet = readTaskSet(original.getTasksetSnapshot());
        RoadNet net = roadNetService.toModel(roadnet);
        List<Task> tasks = toTasks(taskSet);

        SimulationResult result;
        try {
            SimulationEngine engine = new SimulationEngine(net, tasks,
                    SimConfig.defaults(original.getAgvCount(), original.getSeed(), original.getAlgorithm()));
            result = engine.runToCompletion();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(4004, "回放失败：" + ex.getMessage());
        }

        Experiment replay = new Experiment();
        replay.setRoadnetId(original.getRoadnetId());
        replay.setTaskSetId(original.getTaskSetId());
        replay.setAlgorithm(original.getAlgorithm());
        replay.setAgvCount(original.getAgvCount());
        replay.setSeed(original.getSeed());
        replay.setStatus(result.finished() ? "FINISHED" : "FAILED");
        replay.setCreateUser(currentUser.id());
        replay.setRoadnetSnapshot(original.getRoadnetSnapshot());
        replay.setTasksetSnapshot(original.getTasksetSnapshot());
        Experiment savedReplay = experimentRepository.save(replay);

        ExperimentMetric replayMetric = saveMetric(savedReplay.getId(), result);
        boolean consistent = "FINISHED".equals(original.getStatus())
                && result.finished()
                && sameMetrics(originalMetric, replayMetric);

        return new ExperimentReplayResult(original.getId(), savedReplay.getId(), consistent,
                toReport(original, Optional.of(originalMetric)),
                toReport(savedReplay, Optional.of(replayMetric)));
    }

    private void requireReplayable(Experiment experiment) {
        SysUser user = currentUser.get();
        boolean owner = Objects.equals(user.getId(), experiment.getCreateUser());
        if (!owner && !SysUser.ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(4030, "只能回放自己创建的实验");
        }
        if (isBlank(experiment.getRoadnetSnapshot()) || isBlank(experiment.getTasksetSnapshot())) {
            throw new BusinessException(4005, "该实验没有输入快照，无法回放（快照功能上线前的历史记录）");
        }
    }

    private ExperimentMetric saveMetric(Long experimentId, SimulationResult result) {
        ExperimentMetric metric = new ExperimentMetric();
        metric.setExperimentId(experimentId);
        metric.setMakespan(result.makespan());
        metric.setTotalPathLen(round1(result.totalPathLen()));
        metric.setTotalWaitTick(result.totalWaitTick());
        metric.setConflictCount(result.conflictCount());
        return metricRepository.save(metric);
    }

    private boolean sameMetrics(ExperimentMetric a, ExperimentMetric b) {
        return Objects.equals(a.getMakespan(), b.getMakespan())
                && Objects.equals(a.getTotalPathLen(), b.getTotalPathLen())
                && Objects.equals(a.getTotalWaitTick(), b.getTotalWaitTick())
                && Objects.equals(a.getConflictCount(), b.getConflictCount());
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @Transactional(readOnly = true)
    public List<ExperimentReport> history() {
        List<ExperimentReport> reports = new ArrayList<>();
        for (Experiment experiment : experimentRepository.findByCreateUserOrderByIdDesc(currentUser.id())) {
            reports.add(toReport(experiment, metricRepository.findByExperimentId(experiment.getId())));
        }
        return reports;
    }

    @Transactional(readOnly = true)
    public List<ExperimentReport> byMapAndTaskSet(Long roadnetId, Long taskSetId) {
        List<ExperimentReport> reports = new ArrayList<>();
        for (Experiment experiment : experimentRepository.findByRoadnetIdAndTaskSetIdOrderByIdDesc(roadnetId, taskSetId)) {
            reports.add(toReport(experiment, metricRepository.findByExperimentId(experiment.getId())));
        }
        return reports;
    }

    private List<Task> toTasks(TaskSetPayload payload) {
        return payload.tasks().stream()
                .map(dto -> new Task(dto.id() == null ? 0 : dto.id(), dto.from(), dto.to()))
                .toList();
    }

    private RoadNetPayload readRoadnet(String json) {
        try {
            return objectMapper.readValue(json, RoadNetPayload.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(4002, "路网快照损坏，无法解析：" + ex.getOriginalMessage());
        }
    }

    private TaskSetPayload readTaskSet(String json) {
        try {
            return objectMapper.readValue(json, TaskSetPayload.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(4002, "任务集快照损坏，无法解析：" + ex.getOriginalMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ExperimentReport toReport(Experiment experiment, Optional<ExperimentMetric> metric) {
        ExperimentMetric m = metric.orElse(null);
        return new ExperimentReport(experiment.getId(), experiment.getRoadnetId(), experiment.getTaskSetId(),
                experiment.getAlgorithm(),
                ALGORITHM_NAMES.getOrDefault(experiment.getAlgorithm(), experiment.getAlgorithm()),
                experiment.getAgvCount(), experiment.getSeed(), experiment.getStatus(),
                m == null ? null : m.getMakespan(),
                m == null ? null : m.getTotalPathLen(),
                m == null ? null : m.getTotalWaitTick(),
                m == null ? null : m.getConflictCount(),
                experiment.getCreateTime());
    }
}
