package com.agv.backend.controller;

import com.agv.backend.common.ApiResponse;
import com.agv.backend.dto.ExperimentReport;
import com.agv.backend.dto.ExperimentRequest;
import com.agv.backend.service.ExperimentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/experiment")
public class ExperimentController {

    private final ExperimentService experimentService;

    public ExperimentController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @PostMapping("/run")
    public ApiResponse<List<ExperimentReport>> run(@Valid @RequestBody ExperimentRequest request) {
        return ApiResponse.ok(experimentService.run(request));
    }

    /** 同一路网与任务集下跑全部算法，直接给对比报表用。 */
    @PostMapping("/compare")
    public ApiResponse<List<ExperimentReport>> compare(@Valid @RequestBody ExperimentRequest request) {
        return ApiResponse.ok(experimentService.run(request));
    }

    @GetMapping("/list")
    public ApiResponse<List<ExperimentReport>> history() {
        return ApiResponse.ok(experimentService.history());
    }

    @GetMapping("/by-map")
    public ApiResponse<List<ExperimentReport>> byMap(@RequestParam Long roadnetId, @RequestParam Long taskSetId) {
        return ApiResponse.ok(experimentService.byMapAndTaskSet(roadnetId, taskSetId));
    }
}
