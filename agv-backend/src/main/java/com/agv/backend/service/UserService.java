package com.agv.backend.service;

import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.UserCreateRequest;
import com.agv.backend.dto.UserUpdateRequest;
import com.agv.backend.entity.SysUser;
import com.agv.backend.repository.SysUserRepository;
import com.agv.backend.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private static final Set<String> ROLES = Set.of(SysUser.ROLE_DISPATCHER, SysUser.ROLE_ADMIN);

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public UserService(SysUserRepository userRepository, PasswordEncoder passwordEncoder,
                       CurrentUser currentUser, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SysUser> list() {
        return userRepository.findAll();
    }

    @Transactional
    public SysUser create(UserCreateRequest request) {
        if (!ROLES.contains(request.role())) {
            throw new BusinessException("角色只能是 dispatcher 或 admin");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在：" + request.username());
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setRole(request.role());
        user.setStatus(1);
        SysUser saved = userRepository.save(user);
        auditService.success("USER_CREATE", "USER", saved.getId());
        return saved;
    }

    @Transactional
    public SysUser update(Long id, UserUpdateRequest request) {
        SysUser user = requireUser(id);
        if (request.role() != null) {
            if (!ROLES.contains(request.role())) {
                throw new BusinessException("角色只能是 dispatcher 或 admin");
            }
            user.setRole(request.role());
        }
        if (request.realName() != null) {
            user.setRealName(request.realName());
        }
        if (request.status() != null) {
            if (id.equals(currentUser.id()) && request.status() == 0) {
                throw new BusinessException("不能停用当前登录账号");
            }
            user.setStatus(request.status());
        }
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 6) {
                throw new BusinessException("密码长度至少 6 位");
            }
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        SysUser saved = userRepository.save(user);
        auditService.success("USER_UPDATE", "USER", saved.getId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (id.equals(currentUser.id())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        userRepository.delete(requireUser(id));
        auditService.success("USER_DELETE", "USER", id);
    }

    private SysUser requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(4040, "用户不存在：" + id));
    }
}
