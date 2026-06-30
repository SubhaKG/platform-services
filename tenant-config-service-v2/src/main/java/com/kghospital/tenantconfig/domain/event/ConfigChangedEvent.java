package com.kghospital.tenantconfig.domain.event;

import com.kghospital.tenantconfig.domain.enums.ResolutionScope;

import java.time.Instant;
import java.util.UUID;

/**
 * §11 Open Issue #5 — Kafka push notification for cache strategy.
 * Spec leaves the choice between Kafka push and TTL pull undecided;
 * this service publishes BOTH: the 60s TTL cache (FR-10, always on)
 * AND this event for any consuming module that prefers push-based
 * invalidation over waiting for TTL expiry.
 */
public record ConfigChangedEvent(
    UUID eventId,
    String tenantId,
    String configKey,
    ResolutionScope scope,
    String oldValue,
    String newValue,
    UUID actorId,
    Instant occurredAt
) {}
