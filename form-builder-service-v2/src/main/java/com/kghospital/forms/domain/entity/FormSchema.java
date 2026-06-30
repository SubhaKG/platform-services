package com.kghospital.forms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-004 §3.7.1 — form_schemas.
 *
 * "Once a schema version is published (is_published = true), its
 * definition JSONB field is immutable" per §3.8.3 Legal Versioning.
 * No @Setter on definition once published — enforced at service layer
 * since JPA entities need setters for Hibernate, but the service checks
 * is_published before allowing any mutation.
 */
@Entity
@Table(name = "form_schemas",
    indexes = {
        @Index(name = "idx_fs_name",      columnList = "name"),
        @Index(name = "idx_fs_tenant",    columnList = "tenant_id"),
        @Index(name = "idx_fs_published", columnList = "is_published")
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uq_schema_name_version_tenant",
        columnNames = {"tenant_id", "name", "version"})
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FormSchema {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** Unique within tenant DB per version — e.g. "vital-signs" */
    @Column(nullable = false, length = 128)
    private String name;

    /** Monotonically incrementing per name within tenant */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * Full schema: fields[], types, enableWhen rules, fhir_mapping.
     * Validated against schema spec on write per §3.7.1.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String definition;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
