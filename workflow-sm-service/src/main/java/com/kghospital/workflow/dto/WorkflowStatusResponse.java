package com.kghospital.workflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowStatusResponse(
    UUID instanceId,
    UUID entityId,
    String entityType,
    String currentState,
    Instant stateEnteredAt,
    String tenantId,
    List<HistoryEntry> history
) {
    public record HistoryEntry(
        UUID id,
        String fromState,
        String toState,
        String actionCode,
        String actorRole,
        String transitionType,
        Instant transitionedAt
    ) {}
}
