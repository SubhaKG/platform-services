package com.kghospital.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-001 §9.2.4 — emergency_audit_queue table.
 * Tier 2 break-glass deferred audit when PLAT-002 is unavailable.
 * Replayed automatically by @Scheduled job every 30s.
 */
@Entity
@Table(name = "emergency_audit_queue", indexes = {
    @Index(name = "idx_eaq_replayed", columnList = "replayed_at"),
    @Index(name = "idx_eaq_queued",   columnList = "queued_at")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EmergencyAuditQueue {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Full audit payload: actor_id, reason, patient_id, tenant_id,
     * timestamp, severity per §9.2.4
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "break_glass_event", columnDefinition = "jsonb", nullable = false)
    private String breakGlassEvent;

    @Column(name = "queued_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant queuedAt = Instant.now();

    /** null = pending; non-null = successfully replayed to PLAT-002 */
    @Column(name = "replayed_at")
    private Instant replayedAt;

    /** Alert fires if retry_count > 10 per §9.2.4 */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
}
