package com.agv.backend.service;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.SimulationResult;
import com.agv.algorithm.sim.Task;
import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.ExperimentReport;
import com.agv.backend.dto.ExperimentRequest;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.entity.Experiment;
import com.agv.backend.entity.ExperimentMetric;
import com.agv.backend.entity.TaskSet;
import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.security.CurrentUser;
import com.agv.backend.support.TaskSetValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 离线对比实验：同一路网、同一任务集、同一 seed 换算法重跑，指标入库。
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

    public ExperimentService(RoadNetService roadNetService, TaskSetService taskSetService,
                             TaskSetValidator taskSetValidator, ExperimentRepository experimentRepository,
                             ExperimentMetricRepository metricRepository, CurrentUser currentUser) {
        this.roadNetService = roadNetService;
        this.taskSetService = taskSetService;
        this.taskSetValidator = taskSetValidator;
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
        this.currentUser = currentUser;
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
        List<Task> tasks = payload.tasks().stream()
                .map(dto -> new Task(dto.id() == null ? 0 : dto.id(), dto.from(), dto.to()))
                .toList();
        int agvCount = request.agvCount() == null ? 3 : request.agvCount();
        long seed = request.seed() == null ? 20261107L : request.seed();

        List<ExperimentReport> reports = new ArrayList<>();
        for (String algorithm : algorithms) {
            reports.add(runOne(net, tasks, algorithm, agvCount, seed, request));
        }
        return reports;
    }

    private ExperimentReport runOne(RoadNet net, List<Task> tasks, String algorithm,
                                    int agvCount, long seed, ExperimentRequest request) {
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
        Experiment saved = experimentRepository.save(experiment);

        ExperimentMetric metric = new ExperimentMetric();
        metric.setExperimentId(saved.getId());
        metric.setMakespan(result.makespan());
        metric.setTotalPathLen(Math.round(result.totalPathLen() * 10.0) / 10.0);
        metric.setTotalWaitTick(result.totalWaitTick());
        metric.setConflictCount(result.conflictCount());
        metricRepository.save(metric);

        return toReport(saved, Optional.of(metric));
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
