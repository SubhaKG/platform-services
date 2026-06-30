package com.kghospital.escalation.domain.event;
import java.time.Instant;
import java.util.UUID;
public record DomainEventEnvelope(
    String eventType, String tenantId, UUID entityId,
    String entityType, UUID correlationId, Instant occurredAt
) {}
