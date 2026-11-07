package com.agv.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空") @Size(max = 64) String username,
        @NotBlank(message = "密码不能为空") @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间") String password,
        String realName,
        @NotBlank(message = "角色不能为空") String role) {
}
