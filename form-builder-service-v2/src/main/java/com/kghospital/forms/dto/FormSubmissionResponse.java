package com.kghospital.forms.dto;

import java.time.Instant;
import java.util.UUID;

public record FormSubmissionResponse(
    UUID id, UUID schemaId, Integer schemaVersion,
    UUID patientId, UUID encounterId, UUID actorId, String actorRole,
    String responses, String fhirResource,
    Instant timestamp, String idempotencyKey, Instant createdAt
) {}
