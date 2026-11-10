package com.agv.backend.repository;

import com.agv.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActionContainingIgnoreCaseAndUsernameContainingIgnoreCase(
            String action, String username, Pageable pageable);
}
