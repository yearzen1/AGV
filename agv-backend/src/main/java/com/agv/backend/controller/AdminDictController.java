package com.agv.backend.controller;

import com.agv.backend.common.ApiResponse;
import com.agv.backend.dto.DictRequest;
import com.agv.backend.entity.SysDict;
import com.agv.backend.service.DictService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/dict")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDictController {

    private final DictService dictService;

    public AdminDictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    public ApiResponse<List<SysDict>> list(@RequestParam(required = false) String dictType) {
        return ApiResponse.ok(dictService.listAll(dictType));
    }

    @PostMapping
    public ApiResponse<SysDict> create(@Valid @RequestBody DictRequest request) {
        return ApiResponse.ok(dictService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysDict> update(@PathVariable Long id, @Valid @RequestBody DictRequest request) {
        return ApiResponse.ok(dictService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return ApiResponse.ok();
    }
}
