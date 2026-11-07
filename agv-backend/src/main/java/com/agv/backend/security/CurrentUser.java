package com.agv.backend.security;

import com.agv.backend.common.BusinessException;
import com.agv.backend.entity.SysUser;
import com.agv.backend.repository.SysUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 取当前登录用户。JWT 里只存用户名，这里按用户名回查一次库（带 JPA 一级缓存）。
 */
@Component
public class CurrentUser {

    private final SysUserRepository userRepository;

    public CurrentUser(SysUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public SysUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(4010, "未登录");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessException(4010, "登录用户不存在"));
    }

    public Long id() {
        return get().getId();
    }
}
