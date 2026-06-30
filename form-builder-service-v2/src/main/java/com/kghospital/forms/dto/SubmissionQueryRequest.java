package com.kghospital.forms.dto;

import java.time.Instant;
import java.util.UUID;

/** §FR-07 — default 50, max 200 */
public record SubmissionQueryRequest(
    UUID patientId, UUID encounterId, UUID schemaId, UUID actorId,
    Instant from, Instant to, int page, int size
) {
    public SubmissionQueryRequest {
        if (size <= 0) size = 50;
        if (size > 200) size = 200;
    }
}
