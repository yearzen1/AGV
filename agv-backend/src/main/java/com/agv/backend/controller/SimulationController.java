package com.agv.backend.controller;

import com.agv.backend.common.ApiResponse;
import com.agv.backend.dto.StartSimRequest;
import com.agv.backend.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sim")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(@Valid @RequestBody StartSimRequest request) {
        return ApiResponse.ok(simulationService.start(request));
    }

    @PostMapping("/pause")
    public ApiResponse<Map<String, Object>> pause() {
        return ApiResponse.ok(simulationService.pause());
    }

    @PostMapping("/resume")
    public ApiResponse<Map<String, Object>> resume() {
        return ApiResponse.ok(simulationService.resume());
    }

    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        return ApiResponse.ok(simulationService.reset());
    }

    @PostMapping("/speed")
    public ApiResponse<Map<String, Object>> speed(@RequestParam double value) {
        return ApiResponse.ok(simulationService.setSpeed(value));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(simulationService.status());
    }
}
