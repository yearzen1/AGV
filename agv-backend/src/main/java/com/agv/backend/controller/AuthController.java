package com.agv.backend.controller;

import com.agv.backend.common.ApiResponse;
import com.agv.backend.dto.LoginRequest;
import com.agv.backend.dto.LoginResponse;
import com.agv.backend.dto.UserInfo;
import com.agv.backend.entity.SysUser;
import com.agv.backend.security.CurrentUser;
import com.agv.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        SysUser user = currentUser.get();
        return ApiResponse.ok(authService.toInfo(user));
    }
}
