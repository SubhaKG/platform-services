package com.kghospital.tenantconfig.repository;

import com.kghospital.tenantconfig.domain.entity.ConfigAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigAuditLogRepository extends JpaRepository<ConfigAuditLog, java.util.UUID> {
    /** §FR-12 — chronological history for a key at a hospital */
    Page<ConfigAuditLog> findByTenantIdAndConfigKeyOrderByChangedAtAsc(
        String tenantId, String configKey, Pageable pageable);
}
