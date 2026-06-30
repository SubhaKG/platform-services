package com.kghospital.workflow.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "entity_workflow_instance", indexes = {
    @Index(name = "idx_ewi_entity", columnList = "entity_id,entity_type"),
    @Index(name = "idx_ewi_tenant", columnList = "tenant_id"),
    @Index(name = "idx_ewi_state",  columnList = "current_state")
},  uniqueConstraints = @UniqueConstraint(
        columnNames = {"entity_id", "entity_type", "tenant_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EntityWorkflowInstance {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowDefinition workflow;

    @Column(name = "current_state", nullable = false)
    private String currentState;

    @Column(name = "state_entered_at", nullable = false)
    @Builder.Default
    private Instant stateEnteredAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("transitionedAt DESC")
    @Builder.Default
    private List<TransitionHistory> history = new ArrayList<>();
}
