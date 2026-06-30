package com.kghospital.integrationregistry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** §6.4 FR-11 — Integration Engine reports call outcome */
public record HealthReportRequest(
    @NotBlank String hospitalId,
    @NotBlank String adapterId,
    @NotNull  Boolean success,
    Long latencyMs,
    String errorMessage
) {}
