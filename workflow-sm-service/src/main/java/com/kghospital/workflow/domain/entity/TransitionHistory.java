package com.kghospital.workflow.domain.entity;

import com.kghospital.workflow.domain.enums.TransitionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transition_history", indexes = {
    @Index(name = "idx_th_instance",    columnList = "instance_id"),
    @Index(name = "idx_th_transitioned",columnList = "transitioned_at DESC"),
    @Index(name = "idx_th_correlation", columnList = "correlation_id")
})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransitionHistory {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private EntityWorkflowInstance instance;

    @Column(name = "from_state")
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "actor_subject")
    private String actorSubject;

    @Column(name = "actor_role")
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_type", nullable = false)
    @Builder.Default
    private TransitionType transitionType = TransitionType.NORMAL;

    private String comment;

    @Column(name = "transitioned_at", nullable = false)
    @Builder.Default
    private Instant transitionedAt = Instant.now();

    @Column(name = "correlation_id")
    private UUID correlationId;
}
