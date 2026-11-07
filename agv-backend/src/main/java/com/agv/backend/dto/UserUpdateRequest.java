package com.agv.backend.dto;

public record UserUpdateRequest(String realName, String role, Integer status, String password) {
}
