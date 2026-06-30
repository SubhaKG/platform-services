package com.kghospital.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

public record AlertInstanceResponse(
    UUID id, String alertType, String status, String recipientRule,
    List<UUID> resolvedRecipientIds, Map context,
    Integer currentStep, Instant nextEscalationAt,
    UUID acknowledgedBy, Instant acknowledgedAt, Instant createdAt,
    List<DeliveryAttemptSummary> deliveryAttempts
) {
    public record DeliveryAttemptSummary(
        UUID id, String channel, UUID recipientId, String status,
        Integer attemptNumber, Instant attemptedAt
    ) {}
}
