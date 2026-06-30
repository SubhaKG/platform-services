package com.kghospital.forms.domain.event;
import java.time.Instant;
import java.util.UUID;

/** §3.6.2 — deferred to v1.2, scaffolded now for forward compatibility */
public record SubmissionCreatedEvent(
    UUID submissionId, UUID schemaId, Integer schemaVersion,
    String tenantId, Instant createdAt
) {}
