package com.kghospital.workflow.domain.entity;

import com.kghospital.workflow.domain.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow_definition", indexes = {
    @Index(name = "idx_wf_entity_type", columnList = "entity_type"),
    @Index(name = "idx_wf_tenant",      columnList = "tenant_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WorkflowDefinition {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.ACTIVE;

    /** null = canonical platform definition; non-null = tenant override */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "initial_state", nullable = false)
    private String initialState;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StateDefinition> states = new ArrayList<>();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransitionDefinition> transitions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
