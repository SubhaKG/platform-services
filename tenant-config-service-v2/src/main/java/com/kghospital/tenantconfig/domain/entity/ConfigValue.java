package com.kghospital.tenantconfig.domain.entity;

import com.kghospital.tenantconfig.domain.enums.ConfigValueStatus;
import com.kghospital.tenantconfig.domain.enums.ResolutionScope;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-005 §8.2 — config_values (tenant DB — per hospital).
 *
 * "Unique per (hospital, key, scope, scope_context)." Hospital scope
 * has scope_context = null; department scope has {dept_id}; role
 * scope has {role}. This is the genuinely new structure vs. the old
 * TenantConfigEntry, which only supported one value per key per
 * tenant with no department/role dimension at all.
 */
@Entity
@Table(name = "config_values", indexes = {
    @Index(name = "idx_cv_key",    columnList = "config_key"),
    @Index(name = "idx_cv_scope",  columnList = "scope"),
    @Index(name = "idx_cv_status", columnList = "status"),
    @Index(name = "idx_cv_tenant", columnList = "tenant_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ConfigValue {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Denormalised safety field — isolation is via separate DB per tenant */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** FK to ConfigKeyCatalogue.key (catalogue lives in shared platform DB, not FK-enforced here) */
    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResolutionScope scope;

    /** Null for hospital scope. {dept_id} for department. {role} for role. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_context", columnDefinition = "jsonb")
    private String scopeContext;

    /** Stored as JSONB to support all data_type values uniformly */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ConfigValueStatus status = ConfigValueStatus.ACTIVE;

    @Column(name = "set_by", nullable = false)
    private UUID setBy;

    /** Null for non-clinical keys */
    @Column(name = "approved_by")
    private UUID approvedBy;

    /** When the value became effective. Null while PENDING. */
    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
