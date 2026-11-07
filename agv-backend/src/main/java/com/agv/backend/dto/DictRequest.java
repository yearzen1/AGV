package com.agv.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record DictRequest(
        @NotBlank(message = "字典类型不能为空") String dictType,
        @NotBlank(message = "字典编码不能为空") String dictCode,
        @NotBlank(message = "字典值不能为空") String dictValue,
        Integer sort,
        Integer status) {
}
