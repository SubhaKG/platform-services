package com.kghospital.formbuilder.domain.entity;

import com.kghospital.formbuilder.domain.enums.FormCategory;
import com.kghospital.formbuilder.domain.enums.FormStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "form_definition", indexes = {
    @Index(name = "idx_form_category", columnList = "category"),
    @Index(name = "idx_form_status", columnList = "status"),
    @Index(name = "idx_form_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FormStatus status = FormStatus.DRAFT;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    /** Which tenant owns this form definition */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** Ordered list of field definitions */
    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<FieldDefinition> fields = new ArrayList<>();

    /** JSON blob for conditional logic rules */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditional_rules", columnDefinition = "jsonb")
    private String conditionalRules;

    /** JSON metadata — e.g. printer template, icon, color */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by")
    private UUID createdBy;
}
