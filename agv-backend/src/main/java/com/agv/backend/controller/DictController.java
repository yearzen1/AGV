package com.agv.backend.controller;

import com.agv.backend.common.ApiResponse;
import com.agv.backend.entity.SysDict;
import com.agv.backend.service.DictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dict")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /** 下拉框数据源：/api/dict/algorithm、/api/dict/metric */
    @GetMapping("/{dictType}")
    public ApiResponse<List<SysDict>> list(@PathVariable String dictType) {
        return ApiResponse.ok(dictService.listEnabled(dictType));
    }
}
