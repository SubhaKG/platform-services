package com.kghospital.integrationregistry.domain.entity;

import com.kghospital.integrationregistry.domain.enums.AdapterStatus;
import com.kghospital.integrationregistry.domain.enums.AdapterType;
import com.kghospital.integrationregistry.domain.enums.Protocol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-012 §9.2 — adapter_entries (tenant DB — per hospital).
 *
 * §6.2 SECURITY REQUIREMENT — "Credentials MUST NEVER be stored in the
 * adapter registry database... not even for test environments." This
 * replaces the old AdapterConfig.encryptedCredentials field entirely —
 * credentialRef is an opaque reference key into the secrets manager;
 * the actual secret is NEVER held here, encrypted or not.
 */
@Entity
@Table(name = "adapter_entries", indexes = {
    @Index(name = "idx_ae_tenant",       columnList = "tenant_id"),
    @Index(name = "idx_ae_adapter_id",   columnList = "adapter_id"),
    @Index(name = "idx_ae_status",       columnList = "status")
},  uniqueConstraints = @UniqueConstraint(
        name = "uq_ae_tenant_adapter", columnNames = {"tenant_id", "adapter_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdapterEntry {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** FK to platform-shared adapter_catalogue.adapter_id. One entry per concern per hospital. */
    @Column(name = "adapter_id", nullable = false, length = 64)
    private String adapterId;

    /** Implementation type. Must be in adapter_catalogue.supported_types for this adapter_id. */
    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_type", nullable = false, length = 32)
    private AdapterType adapterType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private AdapterStatus status = AdapterStatus.ACTIVE;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Protocol protocol;

    /** Null for event-driven adapters. e.g. "every_6_hours", "daily_02:00" */
    @Column(name = "sync_frequency", length = 32)
    private String syncFrequency;

    @Column(name = "timeout_ms", nullable = false)
    @Builder.Default
    private Integer timeoutMs = 5000;

    /** {max_attempts, backoff, backoff_base_ms} per §5.1 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_policy", nullable = false, columnDefinition = "jsonb")
    private String retryPolicy;

    /**
     * Reference key into the secrets manager ONLY.
     * e.g. "apollo_chennai/formulary/api_key". Never the actual credential.
     * Null for unauthenticated adapters (rare, per §9.2).
     */
    @Column(name = "credential_ref", length = 256)
    private String credentialRef;

    /** Adapter-type-specific config blob. e.g. {hip_id, hiu_id} for ABDM. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String config;

    // ── Health tracking per §6.4, §9.2 ──────────────────────────────────────

    @Column(name = "last_ping")
    private Instant lastPing;

    @Column(name = "last_success")
    private Instant lastSuccess;

    /** Resets to 0 on any success. Drives ACTIVE → DEGRADED → SUSPENDED. */
    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;

    /** Set when status transitions to DEGRADED */
    @Column(name = "degraded_since")
    private Instant degradedSince;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
