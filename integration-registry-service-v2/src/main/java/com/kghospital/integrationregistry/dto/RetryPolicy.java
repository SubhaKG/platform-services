package com.kghospital.integrationregistry.dto;

import com.kghospital.integrationregistry.domain.enums.BackoffStrategy;
import jakarta.validation.constraints.NotNull;

/** §5.1 — {max_attempts, backoff, backoff_base_ms} */
public record RetryPolicy(
    @NotNull Integer maxAttempts,
    @NotNull BackoffStrategy backoff,
    @NotNull Integer backoffBaseMs
) {}
