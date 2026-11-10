package com.agv.backend.service;

import com.agv.algorithm.model.RoadNet;
import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.SaveTaskSetRequest;
import com.agv.backend.dto.TaskDto;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.dto.TaskSetSummary;
import com.agv.backend.entity.TaskSet;
import com.agv.backend.entity.RoadNetEntity;
import com.agv.backend.repository.TaskSetRepository;
import com.agv.backend.repository.RoadNetRepository;
import com.agv.backend.security.CurrentUser;
import com.agv.backend.support.TaskSetValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TaskSetService {

    private final TaskSetRepository repository;
    private final RoadNetRepository mapRepository;
    private final RoadNetService roadNetService;
    private final TaskSetValidator validator;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public TaskSetService(TaskSetRepository repository, RoadNetRepository mapRepository,
                          RoadNetService roadNetService, TaskSetValidator validator,
                          ObjectMapper objectMapper, CurrentUser currentUser, AuditService auditService) {
        this.repository = repository;
        this.mapRepository = mapRepository;
        this.roadNetService = roadNetService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TaskSetSummary> list(Long roadnetId) {
        List<TaskSet> sets = roadnetId == null
                ? repository.findAllByOrderByIdDesc()
                : repository.findByRoadnetIdOrderByIdDesc(roadnetId);
        Map<Long, String> mapNames = new HashMap<>();
        for (RoadNetEntity map : mapRepository.findAll()) {
            mapNames.put(map.getId(), map.getName());
        }
        List<TaskSetSummary> result = new ArrayList<>();
        for (TaskSet set : sets) {
            TaskSetPayload payload = readPayload(set.getTasksJson());
            result.add(summary(set, mapNames.get(set.getRoadnetId()), payload.tasks().size()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TaskSetPayload detail(Long id) {
        return readPayload(require(id).getTasksJson());
    }

    /** 实验快照用：返回库里的任务集 JSON 原文。 */
    @Transactional(readOnly = true)
    public String rawJson(Long id) {
        return require(id).getTasksJson();
    }

    @Transactional
    public TaskSetSummary create(SaveTaskSetRequest request) {
        TaskSetPayload payload = normalize(request.roadnetId(), request.taskSet());
        TaskSet set = new TaskSet();
        set.setName(request.name());
        set.setRoadnetId(request.roadnetId());
        set.setTasksJson(writePayload(payload));
        set.setCreateUser(currentUser.id());
        TaskSet saved = repository.save(set);
        auditService.success("TASKSET_CREATE", "TASKSET", saved.getId());
        return summary(saved, roadnetName(saved.getRoadnetId()), payload.tasks().size());
    }

    @Transactional
    public TaskSetSummary update(Long id, SaveTaskSetRequest request) {
        TaskSet set = require(id);
        TaskSetPayload payload = normalize(request.roadnetId(), request.taskSet());
        set.setName(request.name());
        set.setRoadnetId(request.roadnetId());
        set.setTasksJson(writePayload(payload));
        TaskSet saved = repository.save(set);
        auditService.success("TASKSET_UPDATE", "TASKSET", saved.getId());
        return summary(saved, roadnetName(saved.getRoadnetId()), payload.tasks().size());
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        auditService.success("TASKSET_DELETE", "TASKSET", id);
    }

    @Transactional(readOnly = true)
    public TaskSet require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(4040, "任务集不存在：" + id));
    }

    private TaskSetSummary summary(TaskSet set, String roadnetName, int taskCount) {
        return new TaskSetSummary(set.getId(), set.getName(), set.getRoadnetId(), roadnetName, taskCount,
                set.getCreateUser(), set.getCreateTime());
    }

    private String roadnetName(Long roadnetId) {
        if (roadnetId == null) {
            return null;
        }
        return mapRepository.findById(roadnetId).map(RoadNetEntity::getName).orElse(null);
    }

    /** 校验并补齐任务编号，保证 id 从 1 连续。 */
    private TaskSetPayload normalize(Long roadnetId, TaskSetPayload payload) {
        if (payload.tasks().isEmpty()) {
            throw new BusinessException("任务集至少需要一条任务");
        }
        Set<Integer> ids = new HashSet<>();
        List<TaskDto> normalized = new ArrayList<>();
        int nextId = 1;
        for (TaskDto dto : payload.tasks()) {
            if (dto.from() == null || dto.from().isBlank() || dto.to() == null || dto.to().isBlank()) {
                throw new BusinessException("任务的起点与终点不能为空");
            }
            int id = dto.id() == null || ids.contains(dto.id()) ? nextId : dto.id();
            ids.add(id);
            nextId = Math.max(nextId, id + 1);
            normalized.add(new TaskDto(id, dto.from(), dto.to()));
        }
        normalized.sort((a, b) -> Integer.compare(a.id(), b.id()));

        RoadNet net = roadNetService.toModel(roadNetService.detail(roadnetId));
        validator.validateOrThrow(net, normalized);
        return new TaskSetPayload(normalized);
    }

    private TaskSetPayload readPayload(String json) {
        try {
            return objectMapper.readValue(json, TaskSetPayload.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(4002, "任务集数据损坏：" + ex.getOriginalMessage());
        }
    }

    private String writePayload(TaskSetPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(4003, "任务集序列化失败");
        }
    }
}
