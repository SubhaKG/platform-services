package com.kghospital.escalation.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Defines when and how to escalate unacknowledged notifications.
 * Tenant admins configure rules — no engineering needed.
 *
 * Example rule:
 *   eventType=ORDER_VERIFIED, initialRole=PHARMACIST, slaMinutes=30,
 *   escalateTo=SENIOR_PHARMACIST, thenTo=CHIEF_PHARMACIST
 */
@Entity
@Table(name = "escalation_rule", indexes = {
    @Index(name = "idx_er_tenant_event", columnList = "tenant_id,event_type")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EscalationRule {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;           // e.g. "ORDER_VERIFIED", "STAT_ORDER_CREATED"

    @Column(name = "initial_role", nullable = false)
    private String initialRole;         // Who should act first

    @Column(name = "sla_minutes", nullable = false)
    private Integer slaMinutes;         // Minutes before escalation triggers

    @Column(name = "escalate_to_role", nullable = false)
    private String escalateToRole;      // First escalation target

    @Column(name = "second_escalate_to_role")
    private String secondEscalateToRole; // Second level (supervisor, HOD)

    @Column(name = "second_sla_minutes")
    private Integer secondSlaMinutes;   // Additional minutes before 2nd escalation

    @Column(name = "notification_channel")
    private String notificationChannel; // CHANNEL for escalation (e.g. SMS for urgent)

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
