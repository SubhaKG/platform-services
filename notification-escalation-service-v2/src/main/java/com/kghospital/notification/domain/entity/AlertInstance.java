package com.kghospital.notification.domain.entity;

import com.kghospital.notification.domain.enums.AlertStatus;
import com.kghospital.notification.domain.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PLAT-003 §3.7.1 — alert_instances.
 *
 * escalation_chain_snapshot is captured at dispatch time and is immutable
 * for this instance per FR-09 — config changes only affect NEW alerts.
 */
@Entity
@Table(name = "alert_instances", indexes = {
    @Index(name = "idx_ai_status",         columnList = "status"),
    @Index(name = "idx_ai_next_escalation",columnList = "next_escalation_at"),
    @Index(name = "idx_ai_tenant",         columnList = "tenant_id"),
    @Index(name = "idx_ai_created",        columnList = "created_at")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AlertInstance {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 64)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private AlertStatus status = AlertStatus.PENDING;

    /** Name of the rule at dispatch time (snapshot) — e.g. admitting_physician */
    @Column(name = "recipient_rule", nullable = false, length = 64)
    private String recipientRule;

    /** Concrete user IDs resolved from PRM */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_recipient_ids", columnDefinition = "jsonb")
    private List<UUID> resolvedRecipientIds;

    /** Caller-supplied context blob — patient_id, order_id, drug_name etc. Minimal data only per §3.5.3 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String context;

    /** Full escalation chain config at dispatch time — immutable per FR-09 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "escalation_chain_snapshot", columnDefinition = "jsonb")
    private String escalationChainSnapshot;

    @Column(name = "current_step", nullable = false)
    @Builder.Default
    private Integer currentStep = 1;

    /** When the escalation timer fires; null if acknowledged */
    @Column(name = "next_escalation_at")
    private Instant nextEscalationAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "alertInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attemptedAt DESC")
    @Builder.Default
    private List<DeliveryAttempt> deliveryAttempts = new java.util.ArrayList<>();
}
