package com.agv.backend.service;

import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.LoginRequest;
import com.agv.backend.dto.LoginResponse;
import com.agv.backend.dto.UserInfo;
import com.agv.backend.entity.SysUser;
import com.agv.backend.repository.SysUserRepository;
import com.agv.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(SysUserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            auditService.failureFor(request.username(), "LOGIN", "USER", request.username(), "用户名或密码错误");
            throw new BusinessException(4012, "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            auditService.failureFor(request.username(), "LOGIN", "USER", request.username(), "账号已停用");
            throw new BusinessException(4011, "账号已停用，请联系管理员");
        }
        auditService.successFor(user.getUsername(), "LOGIN", "USER", user.getUsername());
        return new LoginResponse(jwtService.issue(user), toInfo(user));
    }

    public UserInfo toInfo(SysUser user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }
}
