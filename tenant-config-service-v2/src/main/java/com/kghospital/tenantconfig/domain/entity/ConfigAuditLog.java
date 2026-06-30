package com.kghospital.tenantconfig.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-005 §5.3 FR-11 — local immutable audit log, forwarded to PLAT-002.
 *
 * "Every config write (create, update, approve, reject, delete) MUST be
 * written to PLAT-002 with: key, module, scope, scope_context, old_value,
 * new_value, actor_id, and timestamp. Config audit records are immutable."
 *
 * This table is the local record kept regardless of whether the PLAT-002
 * forward succeeds — §9 integration table says PLAT-002 failure for
 * config audit is "fire-and-forget... does not block config writes, but
 * is flagged as a warning", unlike PLAT-002's own submission audit which
 * blocks. So this local table is the durable source of truth even if
 * the PLAT-002 forward silently fails.
 */
@Entity
@Table(name = "config_audit_log", indexes = {
    @Index(name = "idx_cal_tenant", columnList = "tenant_id"),
    @Index(name = "idx_cal_key",    columnList = "config_key"),
    @Index(name = "idx_cal_changed",columnList = "changed_at DESC")
})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ConfigAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(nullable = false, length = 16)
    private String scope;

    /** dept_id or role context, if applicable */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_context", columnDefinition = "jsonb")
    private String scopeContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    /** create, update, approve, reject, delete */
    @Column(nullable = false, length = 16)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "changed_at", nullable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();

    // No setter — immutable after creation, no UPDATE/DELETE permitted
}
