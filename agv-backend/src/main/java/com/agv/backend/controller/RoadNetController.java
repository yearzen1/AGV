package com.agv.backend.controller;

import com.agv.algorithm.model.ValidationError;
import com.agv.algorithm.model.ValidationResult;
import com.agv.backend.common.ApiResponse;
import com.agv.backend.dto.RoadNetPayload;
import com.agv.backend.dto.RoadNetSummary;
import com.agv.backend.dto.SaveRoadNetRequest;
import com.agv.backend.service.RoadNetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roadnet")
public class RoadNetController {

    private final RoadNetService roadNetService;

    public RoadNetController(RoadNetService roadNetService) {
        this.roadNetService = roadNetService;
    }

    @GetMapping("/list")
    public ApiResponse<List<RoadNetSummary>> list() {
        return ApiResponse.ok(roadNetService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoadNetPayload> detail(@PathVariable Long id) {
        return ApiResponse.ok(roadNetService.detail(id));
    }

    @PostMapping
    public ApiResponse<RoadNetSummary> create(@Valid @RequestBody SaveRoadNetRequest request) {
        return ApiResponse.ok(roadNetService.create(request));
    }

    /** 套模板另存为副本，与新建同一条路径，单独留端点便于前端语义清晰。 */
    @PostMapping("/import")
    public ApiResponse<RoadNetSummary> importNet(@Valid @RequestBody SaveRoadNetRequest request) {
        return ApiResponse.ok(roadNetService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoadNetSummary> update(@PathVariable Long id,
                                              @Valid @RequestBody SaveRoadNetRequest request) {
        return ApiResponse.ok(roadNetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roadNetService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/export")
    public ApiResponse<RoadNetPayload> export(@PathVariable Long id) {
        return ApiResponse.ok(roadNetService.export(id));
    }

    /** 边改边验：不落库，只返回逐条错误。 */
    @PostMapping("/validate")
    public ApiResponse<List<ValidationError>> validate(@RequestBody RoadNetPayload payload) {
        ValidationResult result = roadNetService.validate(payload);
        return ApiResponse.ok(result.errors());
    }
}
