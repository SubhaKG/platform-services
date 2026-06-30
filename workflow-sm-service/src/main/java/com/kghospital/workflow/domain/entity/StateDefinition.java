package com.kghospital.workflow.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "state_definition")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StateDefinition {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowDefinition workflow;

    @Column(name = "state_code", nullable = false)
    private String stateCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_terminal", nullable = false)
    @Builder.Default
    private Boolean isTerminal = false;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    private String description;

    @Column(name = "sla_minutes")
    private Integer slaMinutes;

    @Override
    public String toString() {
        return stateCode;
    }
}
