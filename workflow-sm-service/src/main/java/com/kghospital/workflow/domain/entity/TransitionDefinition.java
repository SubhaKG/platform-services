package com.kghospital.workflow.domain.entity;

import com.kghospital.workflow.domain.enums.TransitionTrigger;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "transition_definition")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransitionDefinition {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowDefinition workflow;

    @Column(name = "from_state", nullable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransitionTrigger trigger = TransitionTrigger.MANUAL;

    @Column(name = "allowed_roles")
    private String allowedRoles;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    private String description;
}
