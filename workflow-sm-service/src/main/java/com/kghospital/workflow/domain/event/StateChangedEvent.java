package com.kghospital.workflow.domain.event;

import java.time.Instant;
import java.util.UUID;

public record StateChangedEvent(
    UUID instanceId,
    UUID entityId,
    String entityType,
    String tenantId,
    String fromState,
    String toState,
    String actionCode,
    String actorSubject,
    String actorRole,
    String comment,
    UUID correlationId,
    Instant occurredAt
) {}
