package com.kghospital.integrationregistry.dto;

import jakarta.validation.Valid;
import java.util.Map;

/** §8.1 PUT /adapters/{id} — endpoint, sync_frequency, config blob */
public record AdapterUpdateRequest(
    String endpoint,
    String syncFrequency,
    Integer timeoutMs,
    @Valid RetryPolicy retryPolicy,
    Map<String, Object> config
) {}
