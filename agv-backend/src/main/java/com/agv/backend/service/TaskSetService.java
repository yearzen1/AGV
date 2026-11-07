package com.agv.backend.service;

import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.SaveTaskSetRequest;
import com.agv.backend.dto.TaskDto;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.dto.TaskSetSummary;
import com.agv.backend.entity.TaskSet;
import com.agv.backend.repository.TaskSetRepository;
import com.agv.backend.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TaskSetService {

    private final TaskSetRepository repository;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;

    public TaskSetService(TaskSetRepository repository, ObjectMapper objectMapper, CurrentUser currentUser) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<TaskSetSummary> list() {
        List<TaskSetSummary> result = new ArrayList<>();
        for (TaskSet set : repository.findAllByOrderByIdDesc()) {
            TaskSetPayload payload = readPayload(set.getTasksJson());
            result.add(new TaskSetSummary(set.getId(), set.getName(), payload.tasks().size(),
                    set.getCreateUser(), set.getCreateTime()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TaskSetPayload detail(Long id) {
        return readPayload(require(id).getTasksJson());
    }

    @Transactional
    public TaskSetSummary create(SaveTaskSetRequest request) {
        TaskSetPayload payload = normalize(request.taskSet());
        TaskSet set = new TaskSet();
        set.setName(request.name());
        set.setTasksJson(writePayload(payload));
        set.setCreateUser(currentUser.id());
        TaskSet saved = repository.save(set);
        return new TaskSetSummary(saved.getId(), saved.getName(), payload.tasks().size(),
                saved.getCreateUser(), saved.getCreateTime());
    }

    @Transactional
    public TaskSetSummary update(Long id, SaveTaskSetRequest request) {
        TaskSet set = require(id);
        TaskSetPayload payload = normalize(request.taskSet());
        set.setName(request.name());
        set.setTasksJson(writePayload(payload));
        TaskSet saved = repository.save(set);
        return new TaskSetSummary(saved.getId(), saved.getName(), payload.tasks().size(),
                saved.getCreateUser(), saved.getCreateTime());
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
    }

    @Transactional(readOnly = true)
    public TaskSet require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(4040, "任务集不存在：" + id));
    }

    /** 校验并补齐任务编号，保证 id 从 1 连续。 */
    private TaskSetPayload normalize(TaskSetPayload payload) {
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
