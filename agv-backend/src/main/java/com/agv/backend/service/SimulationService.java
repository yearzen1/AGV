package com.agv.backend.service;

import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimFrame;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.algorithm.sim.Task;
import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.StartSimRequest;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.entity.Experiment;
import com.agv.backend.entity.ExperimentMetric;
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
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 实时仿真编排：驱动引擎、按 tick 通过 WebSocket 推送帧、落库实验与指标。
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

    private final Object lock = new Object();

    private SimulationEngine engine;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> loop;

    private volatile boolean paused;
    private volatile double speed = 1.0;
    private volatile String status = "IDLE";

    private Long experimentId;
    private Long mapId;
    private Long taskSetId;
    private String algorithm;
    private Integer agvCount;
    private Long seed;

    public SimulationService(RoadNetService roadNetService, TaskSetService taskSetService,
                             TaskSetValidator taskSetValidator, SimSocketHandler socketHandler,
                             ExperimentRepository experimentRepository,
                             ExperimentMetricRepository metricRepository, CurrentUser currentUser) {
        this.roadNetService = roadNetService;
        this.taskSetService = taskSetService;
        this.taskSetValidator = taskSetValidator;
        this.socketHandler = socketHandler;
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
        this.currentUser = currentUser;
    }

    public Map<String, Object> start(StartSimRequest request) {
        synchronized (lock) {
            stopInternal();
            abortExperiment("ABORTED");
            RoadNet net = roadNetService.toModel(roadNetService.detail(request.mapId()));
            TaskSet taskSet = taskSetService.require(request.taskSetId());
            requireMatchingMap(taskSet, request.mapId());
            TaskSetPayload payload = taskSetService.detail(request.taskSetId());
            taskSetValidator.validateOrThrow(net, payload.tasks());
            List<Task> tasks = toTasks(payload);
            long actualSeed = request.seed() == null ? System.currentTimeMillis() : request.seed();
            int count = request.agvCount() == null ? 3 : request.agvCount();

            try {
                engine = new SimulationEngine(net, tasks, SimConfig.defaults(count, actualSeed, request.algorithm()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(4004, "无法启动仿真：" + ex.getMessage());
            }

            mapId = request.mapId();
            taskSetId = request.taskSetId();
            algorithm = request.algorithm();
            agvCount = count;
            seed = actualSeed;
            paused = false;
            speed = 1.0;
            status = "RUNNING";

            experimentId = persistExperiment("RUNNING").getId();
            startScheduler();
            return statusPayload();
        }
    }

    public Map<String, Object> pause() {
        paused = true;
        broadcastStatus("paused");
        return statusPayload();
    }

    public Map<String, Object> resume() {
        paused = false;
        broadcastStatus("resumed");
        return statusPayload();
    }

    public Map<String, Object> reset() {
        synchronized (lock) {
            stopInternal();
            abortExperiment("ABORTED");
            status = "IDLE";
            experimentId = null;
            broadcastStatus("reset");
            return statusPayload();
        }
    }

    public Map<String, Object> setSpeed(double value) {
        synchronized (lock) {
            speed = Math.max(0.25, Math.min(8.0, value));
            if (scheduler != null && !scheduler.isShutdown() && engine != null && !engine.finished()) {
                if (loop != null) {
                    loop.cancel(false);
                }
                loop = scheduler.scheduleAtFixedRate(this::tickSafely, 0, intervalMillis(), TimeUnit.MILLISECONDS);
            }
            broadcastStatus("speed");
            return statusPayload();
        }
    }

    public Map<String, Object> status() {
        return statusPayload();
    }

    private long intervalMillis() {
        return Math.max(10L, (long) (SimConfig.TICK_MILLIS / speed));
    }

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agv-sim");
            thread.setDaemon(true);
            return thread;
        });
        loop = scheduler.scheduleAtFixedRate(this::tickSafely, 0, intervalMillis(), TimeUnit.MILLISECONDS);
    }

    private void tickSafely() {
        if (paused) {
            return;
        }
        SimulationEngine current = engine;
        if (current == null || current.finished()) {
            return;
        }

        SimFrame frame;
        try {
            frame = current.step();
        } catch (RuntimeException ex) {
            log.error("仿真推进失败", ex);
            status = "FAILED";
            stopScheduler();
            abortExperiment("FAILED");
            return;
        }

        // 推送失败属于传输层问题，不能影响仿真本身
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "frame");
            message.put("frame", frame);
            message.put("speed", speed);
            message.put("paused", paused);
            socketHandler.broadcast(message);
        } catch (RuntimeException ex) {
            log.warn("帧推送失败，已忽略本次推送：{}", ex.getMessage());
        }

        if (frame.finished()) {
            finish(current);
        }
    }

    private void finish(SimulationEngine finishedEngine) {
        status = "FINISHED";
        stopScheduler();
        try {
            Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
            if (experiment != null) {
                experiment.setStatus("FINISHED");
                experimentRepository.save(experiment);
                SimFrame.Metrics metrics = finishedEngine.metrics();
                ExperimentMetric metric = new ExperimentMetric();
                metric.setExperimentId(experimentId);
                metric.setMakespan(metrics.makespan());
                metric.setTotalPathLen(metrics.totalPathLen());
                metric.setTotalWaitTick(metrics.totalWaitTick());
                metric.setConflictCount(metrics.conflictCount());
                metricRepository.save(metric);
            }
        } catch (RuntimeException ex) {
            log.warn("实验指标落库失败", ex);
        }
        broadcastStatus("finished");
    }

    private Experiment persistExperiment(String state) {
        Experiment experiment = new Experiment();
        experiment.setMapId(mapId);
        experiment.setTaskSetId(taskSetId);
        experiment.setAlgorithm(algorithm);
        experiment.setAgvCount(agvCount);
        experiment.setSeed(seed);
        experiment.setStatus(state);
        experiment.setCreateUser(currentUser.id());
        return experimentRepository.save(experiment);
    }

    /**
     * 被新仿真替换、手动重置或推进失败时，把仍处于 RUNNING 的实验落一个终态，
     * 避免历史列表里出现永远「运行中」的僵尸记录。
     */
    private void abortExperiment(String state) {
        Long id = experimentId;
        if (id == null) return;
        try {
            experimentRepository.findById(id).ifPresent(experiment -> {
                if ("RUNNING".equals(experiment.getStatus())) {
                    experiment.setStatus(state);
                    experimentRepository.save(experiment);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("实验状态更新失败：{}", ex.getMessage());
        }
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

    private void requireMatchingMap(TaskSet taskSet, Long mapId) {
        if (taskSet.getMapId() == null) {
            throw new BusinessException("任务集「" + taskSet.getName() + "」未绑定路网，请先编辑");
        }
        if (!taskSet.getMapId().equals(mapId)) {
            throw new BusinessException("任务集「" + taskSet.getName() + "」绑定的路网与所选路网不一致");
        }
    }

    private void stopScheduler() {
        if (loop != null) {
            loop.cancel(false);
            loop = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void stopInternal() {
        stopScheduler();
        engine = null;
    }

    private void broadcastStatus(String reason) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "status");
        message.put("reason", reason);
        message.putAll(statusPayload());
        socketHandler.broadcast(message);
    }

    private Map<String, Object> statusPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("paused", paused);
        payload.put("speed", speed);
        payload.put("experimentId", experimentId);
        payload.put("mapId", mapId);
        payload.put("taskSetId", taskSetId);
        payload.put("algorithm", algorithm);
        payload.put("agvCount", agvCount);
        payload.put("seed", seed);
        SimulationEngine current = engine;
        if (current == null) {
            payload.put("tick", 0);
            payload.put("metrics", null);
        } else {
            payload.put("tick", current.tick());
            payload.put("metrics", current.metrics());
        }
        return payload;
    }
    @PreDestroy
    public void shutdown() {
        stopScheduler();
    }
}
