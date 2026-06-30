package com.kghospital.integrationregistry.domain.event;

import java.time.Instant;

/** §8.2 — adapter.credential_rotated — signals Integration Engine to evict credential cache */
public record CredentialRotatedEvent(
    String tenantId, String adapterId, String newCredentialRef, Instant occurredAt
) {}
