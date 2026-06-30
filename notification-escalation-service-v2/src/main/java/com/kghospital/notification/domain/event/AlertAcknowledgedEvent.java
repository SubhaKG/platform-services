package com.kghospital.notification.domain.event;
import java.time.Instant;
import java.util.UUID;
public record AlertAcknowledgedEvent(
    UUID alertInstanceId, String alertType, String tenantId,
    UUID acknowledgedBy, Instant acknowledgedAt
) {}
