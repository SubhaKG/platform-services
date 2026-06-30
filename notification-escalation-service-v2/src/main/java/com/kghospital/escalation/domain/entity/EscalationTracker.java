package com.kghospital.escalation.domain.entity;

import com.kghospital.escalation.domain.enums.EscalationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks each active escalation instance.
 * Created when a domain event arrives that matches an escalation rule.
 * Closed when acknowledged or resolved.
 */
@Entity
@Table(name = "escalation_tracker", indexes = {
    @Index(name = "idx_et_entity",   columnList = "entity_id"),
    @Index(name = "idx_et_status",   columnList = "status"),
    @Index(name = "idx_et_due",      columnList = "escalate_at"),
    @Index(name = "idx_et_tenant",   columnList = "tenant_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EscalationTracker {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;              // The CPOE order / patient / encounter

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private EscalationRule rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EscalationStatus status = EscalationStatus.PENDING;

    @Column(name = "escalation_level")
    @Builder.Default
    private Integer escalationLevel = 0; // 0=initial, 1=first escalation, 2=second

    @Column(name = "escalate_at", nullable = false)
    private Instant escalateAt;          // When to trigger next escalation

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "correlation_id")
    private UUID correlationId;
}
