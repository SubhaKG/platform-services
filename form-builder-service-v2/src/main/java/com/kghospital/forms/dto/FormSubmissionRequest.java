package com.kghospital.forms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/** §3.6.1 FormSubmissionRequest */
public record FormSubmissionRequest(
    @NotNull UUID schemaId,
    @NotNull Integer schemaVersion,
    @NotNull UUID patientId,
    UUID encounterId,
    @NotNull UUID actorId,
    @NotBlank String actorRole,
    @NotNull java.time.Instant timestamp,
    @NotNull Map<String, Object> responses,
    String idempotencyKey
) {}
