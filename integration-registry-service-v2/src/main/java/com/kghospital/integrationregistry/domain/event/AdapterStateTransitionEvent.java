package com.kghospital.integrationregistry.domain.event;

import java.time.Instant;

/** §8.2 — adapter.degraded / adapter.suspended / adapter.recovered */
public record AdapterStateTransitionEvent(
    String tenantId, String adapterId, String fromStatus, String toStatus,
    Integer consecutiveFailures, String lastError, Instant occurredAt
) {}
