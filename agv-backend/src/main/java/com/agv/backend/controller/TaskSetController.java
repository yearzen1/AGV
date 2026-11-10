package com.agv.backend.controller;

import com.agv.backend.common.ApiResponse;
import com.agv.backend.dto.SaveTaskSetRequest;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.dto.TaskSetSummary;
import com.agv.backend.service.TaskSetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/taskset")
public class TaskSetController {

    private final TaskSetService taskSetService;

    public TaskSetController(TaskSetService taskSetService) {
        this.taskSetService = taskSetService;
    }

    @GetMapping("/list")
    public ApiResponse<List<TaskSetSummary>> list(@RequestParam(required = false) Long roadnetId) {
        return ApiResponse.ok(taskSetService.list(roadnetId));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskSetPayload> detail(@PathVariable Long id) {
        return ApiResponse.ok(taskSetService.detail(id));
    }

    @PostMapping
    public ApiResponse<TaskSetSummary> create(@Valid @RequestBody SaveTaskSetRequest request) {
        return ApiResponse.ok(taskSetService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskSetSummary> update(@PathVariable Long id,
                                              @Valid @RequestBody SaveTaskSetRequest request) {
        return ApiResponse.ok(taskSetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskSetService.delete(id);
        return ApiResponse.ok();
    }
}
