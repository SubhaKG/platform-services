package com.kghospital.integrationregistry.dto;

import com.kghospital.integrationregistry.domain.enums.AdapterType;
import com.kghospital.integrationregistry.domain.enums.Protocol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** §6.1 FR-01 — POST /adapters */
public record AdapterEntryRequest(
    @NotBlank String hospitalId,
    @NotBlank String adapterId,
    @NotNull  AdapterType adapterType,
    @NotBlank String endpoint,
    @NotNull  Protocol protocol,
    String syncFrequency,
    Integer timeoutMs,
    @Valid @NotNull RetryPolicy retryPolicy,
    Map<String, Object> config
) {}
