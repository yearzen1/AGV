package com.agv.backend.service;

import com.agv.algorithm.sim.SimConfig;
import com.agv.algorithm.sim.SimFrame;
import com.agv.algorithm.sim.SimulationEngine;
import com.agv.backend.entity.Experiment;
import com.agv.backend.entity.ExperimentMetric;
import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.ws.SimSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 一个用户的一份仿真运行态：引擎、调度线程、暂停/倍速、实验 ID。
 * <p>
 * 帧只发给该用户的 WebSocket 会话；不同用户的 runner 互不共享状态。
 */
final class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final long userId;
    private final Long experimentId;
    private final Long roadnetId;
    private final Long taskSetId;
    private final String algorithm;
    private final Integer agvCount;
    private final Long seed;
    private final SimSocketHandler socketHandler;
    private final ExperimentRepository experimentRepository;
    private final ExperimentMetricRepository metricRepository;

    private SimulationEngine engine;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> loop;

    private volatile boolean paused;
    private volatile double speed = 1.0;
    private volatile String status = "RUNNING";
    private volatile long tick;
    private volatile SimFrame.Metrics lastMetrics;

    SimulationRunner(long userId, SimulationEngine engine, Long experimentId,
                     Long roadnetId, Long taskSetId, String algorithm, Integer agvCount, Long seed,
                     SimSocketHandler socketHandler, ExperimentRepository experimentRepository,
                     ExperimentMetricRepository metricRepository) {
        this.userId = userId;
        this.engine = engine;
        this.experimentId = experimentId;
        this.roadnetId = roadnetId;
        this.taskSetId = taskSetId;
        this.algorithm = algorithm;
        this.agvCount = agvCount;
        this.seed = seed;
        this.socketHandler = socketHandler;
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
    }

    void start() {
        startScheduler();
    }

    Long experimentId() {
        return experimentId;
    }

    boolean isRunning() {
        return "RUNNING".equals(status);
    }

    Map<String, Object> pause() {
        paused = true;
        broadcastStatus("paused");
        return statusPayload();
    }

    Map<String, Object> resume() {
        paused = false;
        broadcastStatus("resumed");
        return statusPayload();
    }

    Map<String, Object> setSpeed(double value) {
        synchronized (this) {
            speed = Math.max(0.25, Math.min(8.0, value));
            if (scheduler != null && !scheduler.isShutdown()
                    && engine != null && !engine.finished()) {
                if (loop != null) {
                    loop.cancel(false);
                }
                loop = scheduler.scheduleAtFixedRate(
                        this::tickSafely, 0, intervalMillis(), TimeUnit.MILLISECONDS);
            }
            broadcastStatus("speed");
            return statusPayload();
        }
    }

    /** 中止当前仿真并释放引擎；用于被新仿真替换或手动重置。 */
    void stopAndAbort(String state, String reason) {
        stopScheduler();
        abortExperiment(state);
        status = "IDLE";
        paused = false;
        engine = null;
        lastMetrics = null;
        tick = 0;
        broadcastIdle(reason);
    }

    void shutdown() {
        stopScheduler();
    }

    Map<String, Object> statusPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("paused", paused);
        payload.put("speed", speed);
        payload.put("experimentId", experimentId);
        payload.put("roadnetId", roadnetId);
        payload.put("taskSetId", taskSetId);
        payload.put("algorithm", algorithm);
        payload.put("agvCount", agvCount);
        payload.put("seed", seed);
        SimulationEngine current = engine;
        if (current == null) {
            payload.put("tick", tick);
            payload.put("metrics", lastMetrics);
            payload.put("agvs", List.of());
        } else {
            payload.put("tick", current.tick());
            payload.put("metrics", current.metrics());
            payload.put("agvs", current.snapshots());
        }
        return payload;
    }

    private long intervalMillis() {
        return Math.max(10L, (long) (SimConfig.TICK_MILLIS / speed));
    }

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agv-sim-" + userId);
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
            log.error("用户 {} 的仿真推进失败", userId, ex);
            status = "FAILED";
            stopScheduler();
            abortExperiment("FAILED");
            broadcastStatus("failed");
            return;
        }

        tick = frame.tick();
        // 推送失败属于传输层问题，不能影响仿真本身
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "frame");
            message.put("frame", frame);
            message.put("speed", speed);
            message.put("paused", paused);
            socketHandler.sendToUser(userId, message);
        } catch (RuntimeException ex) {
            log.warn("用户 {} 的帧推送失败，已忽略本次推送：{}", userId, ex.getMessage());
        }

        if (frame.finished()) {
            finish(current);
        }
    }

    private void finish(SimulationEngine finishedEngine) {
        status = "FINISHED";
        tick = finishedEngine.tick();
        lastMetrics = finishedEngine.metrics();
        stopScheduler();
        engine = null;
        try {
            Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
            if (experiment != null) {
                experiment.setStatus("FINISHED");
                experimentRepository.save(experiment);
                SimFrame.Metrics metrics = lastMetrics;
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

    /**
     * 被新仿真替换、手动重置或推进失败时，把仍处于 RUNNING 的实验落一个终态，
     * 避免历史列表里出现永远「运行中」的僵尸记录。
     */
    private void abortExperiment(String state) {
        if (experimentId == null) {
            return;
        }
        try {
            experimentRepository.findById(experimentId).ifPresent(experiment -> {
                if ("RUNNING".equals(experiment.getStatus())) {
                    experiment.setStatus(state);
                    experimentRepository.save(experiment);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("实验状态更新失败：{}", ex.getMessage());
        }
    }

    private void stopScheduler() {
        synchronized (this) {
            if (loop != null) {
                loop.cancel(false);
                loop = null;
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }

    private void broadcastStatus(String reason) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "status");
        message.put("reason", reason);
        message.putAll(statusPayload());
        socketHandler.sendToUser(userId, message);
    }

    private void broadcastIdle(String reason) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "status");
        message.put("reason", reason);
        message.put("status", "IDLE");
        message.put("paused", false);
        message.put("speed", 1.0);
        message.put("experimentId", null);
        message.put("roadnetId", null);
        message.put("taskSetId", null);
        message.put("algorithm", null);
        message.put("agvCount", null);
        message.put("seed", null);
        message.put("tick", 0);
        message.put("metrics", null);
        message.put("agvs", List.of());
        socketHandler.sendToUser(userId, message);
    }
}
