package com.kghospital.forms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-004 §3.7.2 — form_submissions.
 *
 * schema_version is a SNAPSHOT at submission time — immutable forensic
 * reference per §3.8.3, even if the schema is later superseded.
 */
@Entity
@Table(name = "form_submissions",
    indexes = {
        @Index(name = "idx_sub_patient",   columnList = "patient_id"),
        @Index(name = "idx_sub_encounter", columnList = "encounter_id"),
        @Index(name = "idx_sub_schema",    columnList = "schema_id"),
        @Index(name = "idx_sub_actor",     columnList = "actor_id"),
        @Index(name = "idx_sub_timestamp", columnList = "timestamp"),
        @Index(name = "idx_sub_tenant",    columnList = "tenant_id")
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uq_submission_idempotency", columnNames = "idempotency_key")
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FormSubmission {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "schema_id", nullable = false)
    private UUID schemaId;

    /** Snapshot of version at submission time — immutable reference */
    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_role", nullable = false, length = 64)
    private String actorRole;

    /** Raw field values keyed by field_id — verbatim submitted data */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String responses;

    /** Mapped FHIR R4 resource payload — produced by binding evaluation on ingest */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fhir_resource", nullable = false, columnDefinition = "jsonb")
    private String fhirResource;

    /** Caller-supplied UTC clinical event time — immutable */
    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
