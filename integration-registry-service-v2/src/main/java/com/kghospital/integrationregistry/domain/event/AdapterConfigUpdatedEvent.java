package com.kghospital.integrationregistry.domain.event;

import java.time.Instant;

/** §8.2 — adapter.config_updated — signals Integration Engine to evict config cache */
public record AdapterConfigUpdatedEvent(
    String tenantId, String adapterId, Instant occurredAt
) {}
