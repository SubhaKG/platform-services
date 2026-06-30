package com.kghospital.workflow.dto;

import java.time.Instant;
import java.util.UUID;

public record TransitionResponse(
    UUID instanceId,
    UUID entityId,
    String entityType,
    String fromState,
    String toState,
    String actionCode,
    Instant transitionedAt
) {}
