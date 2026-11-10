package com.agv.backend.service;

import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.PageResponse;
import com.agv.backend.entity.AuditLog;
import com.agv.backend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 业务审计：只记录有业务含义的写操作。
 * <p>
 * 用独立事务提交，业务回滚不会带走日志；提交或写入失败只打 WARN，绝不影响业务。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final TransactionTemplate auditTransaction;

    public AuditService(AuditLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.auditTransaction = new TransactionTemplate(transactionManager);
        this.auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void success(String action, String targetType, Object targetId) {
        record(resolveUsername(), action, targetType, targetId, AuditLog.SUCCESS, null);
    }

    public void failure(String action, String targetType, Object targetId, String detail) {
        record(resolveUsername(), action, targetType, targetId, AuditLog.FAILURE, detail);
    }

    /** 登录场景没有 SecurityContext，操作人由调用方显式传入。 */
    public void successFor(String username, String action, String targetType, Object targetId) {
        record(username, action, targetType, targetId, AuditLog.SUCCESS, null);
    }

    public void failureFor(String username, String action, String targetType, Object targetId, String detail) {
        record(username, action, targetType, targetId, AuditLog.FAILURE, detail);
    }

    public PageResponse<AuditLog> page(int page, int size, String action, String username) {
        if (page < 0) {
            throw new BusinessException(4000, "page 不能小于 0");
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(4000, "size 必须在 1 到 100 之间");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<AuditLog> result = repository.findByActionContainingIgnoreCaseAndUsernameContainingIgnoreCase(
                blankToEmpty(action), blankToEmpty(username), pageable);
        return PageResponse.of(result);
    }

    private void record(String username, String action, String targetType, Object targetId,
                        String result, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUsername(cut(username, 64));
            entry.setAction(cut(action, 48));
            entry.setTargetType(cut(targetType, 32));
            entry.setTargetId(targetId == null ? null : cut(String.valueOf(targetId), 64));
            entry.setResult(cut(result, 16));
            entry.setDetail(cut(detail, 500));
            entry.setIp(cut(currentIp(), 64));
            auditTransaction.executeWithoutResult(status -> repository.save(entry));
        } catch (RuntimeException ex) {
            log.warn("审计日志写入失败，已忽略：{}", ex.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    private String currentIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
