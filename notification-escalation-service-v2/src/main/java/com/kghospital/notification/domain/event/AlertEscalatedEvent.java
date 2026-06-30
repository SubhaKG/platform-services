package com.kghospital.notification.domain.event;
import java.time.Instant;
import java.util.UUID;
public record AlertEscalatedEvent(
    UUID alertInstanceId, String alertType, String tenantId,
    Integer step, Instant escalatedAt
) {}
