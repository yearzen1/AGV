package com.agv.backend.controller;

import com.agv.backend.common.ApiResponse;
import com.agv.backend.dto.UserCreateRequest;
import com.agv.backend.dto.UserUpdateRequest;
import com.agv.backend.entity.SysUser;
import com.agv.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<SysUser>> list() {
        return ApiResponse.ok(userService.list());
    }

    @PostMapping
    public ApiResponse<SysUser> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysUser> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }
}
