package com.agv.backend.service;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.Task;
import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.StartSimRequest;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.entity.Experiment;
import com.agv.backend.entity.TaskSet;
import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.security.CurrentUser;
import com.agv.backend.support.TaskSetValidator;
import com.agv.backend.ws.SimSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时仿真编排：按用户维护一份 {@link SimulationRunner}，驱动引擎、推送帧、落库实验与指标。
 * <p>
 * 引擎本身不依赖 Spring，这里只做调度与 IO。
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final RoadNetService roadNetService;
    private final TaskSetService taskSetService;
    private final TaskSetValidator taskSetValidator;
    private final SimSocketHandler socketHandler;
    private final ExperimentRepository experimentRepository;
    private final ExperimentMetricRepository metricRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final int maxConcurrent;

    private final Object lock = new Object();
    private final Map<Long, SimulationRunner> runners = new ConcurrentHashMap<>();

    public SimulationService(RoadNetService roadNetService, TaskSetService taskSetService,
                             TaskSetValidator taskSetValidator, SimSocketHandler socketHandler,
                             ExperimentRepository experimentRepository,
                             ExperimentMetricRepository metricRepository, CurrentUser currentUser,
                             AuditService auditService,
                             @Value("${agv.sim.max-concurrent:5}") int maxConcurrent) {
        this.roadNetService = roadNetService;
        this.taskSetService = taskSetService;
        this.taskSetValidator = taskSetValidator;
        this.socketHandler = socketHandler;
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public Map<String, Object> start(StartSimRequest request) {
        Long userId = currentUser.id();

        RoadNet net = roadNetService.toModel(roadNetService.detail(request.roadnetId()));
        TaskSet taskSet = taskSetService.require(request.taskSetId());
        requireMatchingMap(taskSet, request.roadnetId());
        TaskSetPayload payload = taskSetService.detail(request.taskSetId());
        taskSetValidator.validateOrThrow(net, payload.tasks());
        List<Task> tasks = toTasks(payload);
        long actualSeed = request.seed() == null ? System.currentTimeMillis() : request.seed();
        int count = request.agvCount() == null ? 3 : request.agvCount();

        SimulationEngine engine;
        try {
            engine = new SimulationEngine(net, tasks, SimConfig.defaults(count, actualSeed, request.algorithm()));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(4004, "无法启动仿真：" + ex.getMessage());
        }

        String roadnetJson = roadNetService.rawJson(request.roadnetId());
        String tasksetJson = taskSetService.rawJson(request.taskSetId());

        synchronized (lock) {
            long runningOthers = runners.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(userId))
                    .filter(entry -> entry.getValue().isRunning())
                    .count();
            if (runningOthers >= maxConcurrent) {
                throw new BusinessException(4290,
                        "并发仿真数已达上限（" + maxConcurrent + "），请稍后再试");
            }

            SimulationRunner existing = runners.remove(userId);
            if (existing != null) {
                existing.stopAndAbort("ABORTED", "replaced");
            }

            Experiment experiment = persistExperiment("RUNNING", userId, request, count, actualSeed,
                    roadnetJson, tasksetJson);
            auditService.success("SIM_START", "SIM", experiment.getId());

            SimulationRunner runner = new SimulationRunner(userId, engine, experiment.getId(),
                    request.roadnetId(), request.taskSetId(), request.algorithm(), count, actualSeed,
                    socketHandler, experimentRepository, metricRepository);
            runners.put(userId, runner);
            runner.start();
            return runner.statusPayload();
        }
    }

    public Map<String, Object> pause() {
        return requireRunning().pause();
    }

    public Map<String, Object> resume() {
        return requireRunning().resume();
    }

    public Map<String, Object> reset() {
        Long userId = currentUser.id();
        synchronized (lock) {
            SimulationRunner runner = runners.remove(userId);
            if (runner != null) {
                runner.stopAndAbort("ABORTED", "reset");
                auditService.success("SIM_RESET", "SIM", runner.experimentId());
            }
            return idlePayload();
        }
    }

    public Map<String, Object> setSpeed(double value) {
        if (!Double.isFinite(value)) {
            throw new BusinessException(4000, "速度倍率必须是有限数值");
        }
        return requireRunning().setSpeed(value);
    }

    public Map<String, Object> status() {
        SimulationRunner runner = runners.get(currentUser.id());
        return runner == null ? idlePayload() : runner.statusPayload();
    }

    /** 测试与停机用：停掉全部用户的仿真，不依赖 SecurityContext。 */
    public void resetAll() {
        synchronized (lock) {
            for (SimulationRunner runner : runners.values()) {
                runner.stopAndAbort("ABORTED", "reset");
            }
            runners.clear();
        }
    }

    private SimulationRunner requireRunning() {
        SimulationRunner runner = runners.get(currentUser.id());
        if (runner == null || !runner.isRunning()) {
            throw new BusinessException(4004, "当前没有运行中的仿真");
        }
        return runner;
    }

    private Experiment persistExperiment(String state, Long userId, StartSimRequest request,
                                         int count, long actualSeed,
                                         String roadnetJson, String tasksetJson) {
        Experiment experiment = new Experiment();
        experiment.setRoadnetId(request.roadnetId());
        experiment.setTaskSetId(request.taskSetId());
        experiment.setAlgorithm(request.algorithm());
        experiment.setAgvCount(count);
        experiment.setSeed(actualSeed);
        experiment.setStatus(state);
        experiment.setCreateUser(userId);
        experiment.setRoadnetSnapshot(roadnetJson);
        experiment.setTasksetSnapshot(tasksetJson);
        return experimentRepository.save(experiment);
    }

    private Map<String, Object> idlePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "IDLE");
        payload.put("paused", false);
        payload.put("speed", 1.0);
        payload.put("experimentId", null);
        payload.put("roadnetId", null);
        payload.put("taskSetId", null);
        payload.put("algorithm", null);
        payload.put("agvCount", null);
        payload.put("seed", null);
        payload.put("tick", 0);
        payload.put("metrics", null);
        return payload;
    }

    /**
     * 后端重启会丢失内存中的仿真线程；把上次进程遗留的 RUNNING 实验补成终态，
     * 否则历史列表里会永远显示「运行中」。
     */
    @PostConstruct
    void recoverStaleExperiments() {
        try {
            List<Experiment> stale = experimentRepository.findByStatus("RUNNING");
            for (Experiment experiment : stale) {
                experiment.setStatus("ABORTED");
            }
            if (!stale.isEmpty()) {
                experimentRepository.saveAll(stale);
                log.info("已将 {} 条遗留的 RUNNING 实验标记为 ABORTED", stale.size());
            }
        } catch (RuntimeException ex) {
            log.warn("遗留实验状态恢复失败：{}", ex.getMessage());
        }
    }

    private List<Task> toTasks(TaskSetPayload payload) {
        return payload.tasks().stream()
                .map(dto -> new Task(dto.id() == null ? 0 : dto.id(), dto.from(), dto.to()))
                .toList();
    }

    private void requireMatchingMap(TaskSet taskSet, Long roadnetId) {
        if (taskSet.getRoadnetId() == null) {
            throw new BusinessException("任务集「" + taskSet.getName() + "」未绑定路网，请先编辑");
        }
        if (!taskSet.getRoadnetId().equals(roadnetId)) {
            throw new BusinessException("任务集「" + taskSet.getName() + "」绑定的路网与所选路网不一致");
        }
    }

    @PreDestroy
    public void shutdown() {
        resetAll();
    }
}
