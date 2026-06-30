package com.kghospital.notification.domain.event;
import java.time.Instant;
public record DeadLetterEvent(
    String originalAlertType, String hospitalId, String reason,
    String rawPayload, Instant occurredAt
) {}
