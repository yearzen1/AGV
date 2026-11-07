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

    public AuthService(SysUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(4012, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(4012, "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw new BusinessException(4011, "账号已停用，请联系管理员");
        }
        return new LoginResponse(jwtService.issue(user), toInfo(user));
    }

    public UserInfo toInfo(SysUser user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }
}
