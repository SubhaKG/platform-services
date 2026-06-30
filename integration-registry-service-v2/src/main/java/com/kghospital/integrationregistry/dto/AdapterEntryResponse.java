package com.kghospital.integrationregistry.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * §6.2 FR-07 — "The GET endpoint returns the credential_ref string
 * only. Any API response containing a credential value is a critical
 * security defect." This response type structurally cannot leak a
 * credential — there is no field for the actual secret anywhere.
 */
public record AdapterEntryResponse(
    UUID id, String hospitalId, String adapterId, String adapterType,
    String status, String endpoint, String protocol, String syncFrequency,
    Integer timeoutMs, String retryPolicy, String credentialRef, String config,
    HealthBlock health, Instant updatedAt
) {
    public record HealthBlock(
        Instant lastPing, Instant lastSuccess,
        Integer consecutiveFailures, Instant degradedSince
    ) {}
}
