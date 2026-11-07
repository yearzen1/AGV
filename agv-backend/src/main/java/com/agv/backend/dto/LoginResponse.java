package com.agv.backend.dto;

public record LoginResponse(String token, UserInfo user) {
}
