package com.kghospital.audit.domain.entity;

import com.kghospital.audit.domain.enums.ActorRole;
import com.kghospital.audit.domain.enums.AuditAction;
import com.kghospital.audit.domain.enums.CpoeEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events",
    indexes = {
        @Index(name = "idx_ae_patient_id",   columnList = "patient_id"),
        @Index(name = "idx_ae_encounter_id",  columnList = "encounter_id"),
        @Index(name = "idx_ae_actor_id",      columnList = "actor_id"),
        @Index(name = "idx_ae_event_type",    columnList = "event_type"),
        @Index(name = "idx_ae_timestamp",     columnList = "timestamp"),
        @Index(name = "idx_ae_resource",      columnList = "resource_type,resource_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_idempotency_key", columnNames = "idempotency_key")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 64, nullable = false)
    private CpoeEventType eventType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 64, nullable = false)
    private ActorRole actorRole;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "resource_type", length = 64, nullable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 64, nullable = false)
    private AuditAction action;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
