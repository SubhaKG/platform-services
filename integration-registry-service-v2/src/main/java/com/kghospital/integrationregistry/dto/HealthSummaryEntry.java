package com.kghospital.integrationregistry.dto;

import java.time.Instant;

/** §6.4 FR-14 — health summary for the admin dashboard */
public record HealthSummaryEntry(
    String adapterId, String adapterType, String status,
    Instant lastSuccess, Integer consecutiveFailures, Instant degradedSince
) {}
