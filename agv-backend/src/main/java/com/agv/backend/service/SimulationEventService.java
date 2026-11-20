package com.agv.backend.service;

import com.agv.algorithm.sim.SimEvent;
import com.agv.backend.dto.ExperimentEventLog;
import com.agv.backend.dto.ExperimentEventView;
import com.agv.backend.entity.Experiment;
import com.agv.backend.entity.SimulationEventEntity;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.repository.SimulationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务级事件的落库与查询。单实验最多保存 {@link #MAX_EVENTS} 条，超出部分丢弃并记录截断标记。
 */
@Service
public class SimulationEventService {

    public static final int MAX_EVENTS = 5_000;

    private final SimulationEventRepository eventRepository;
    private final ExperimentRepository experimentRepository;

    public SimulationEventService(SimulationEventRepository eventRepository,
                                  ExperimentRepository experimentRepository) {
        this.eventRepository = eventRepository;
        this.experimentRepository = experimentRepository;
    }

    @Transactional
    public void record(Long experimentId, List<SimEvent> events) {
        if (experimentId == null || events == null || events.isEmpty()) {
            return;
        }
        List<SimulationEventEntity> rows = new ArrayList<>();
        int limit = Math.min(events.size(), MAX_EVENTS);
        for (int i = 0; i < limit; i++) {
            SimEvent event = events.get(i);
            SimulationEventEntity row = new SimulationEventEntity();
            row.setExperimentId(experimentId);
            row.setSeqNo(i + 1);
            row.setTick(event.tick());
            row.setAgvId(event.agvId());
            row.setType(event.type().name());
            row.setPlace(event.place());
            row.setDetail(event.detail());
            rows.add(row);
        }
        eventRepository.saveAll(rows);
        if (events.size() > MAX_EVENTS) {
            experimentRepository.findById(experimentId).ifPresent(experiment -> {
                experiment.setEventTruncated(true);
                experimentRepository.save(experiment);
            });
        }
    }

    @Transactional(readOnly = true)
    public ExperimentEventLog list(Experiment experiment) {
        List<ExperimentEventView> views = eventRepository
                .findByExperimentIdOrderBySeqNoAsc(experiment.getId()).stream()
                .map(row -> new ExperimentEventView(row.getSeqNo(), row.getTick(), row.getAgvId(),
                        row.getType(), row.getPlace(), row.getDetail()))
                .toList();
        return new ExperimentEventLog(views, Boolean.TRUE.equals(experiment.getEventTruncated()));
    }
}
