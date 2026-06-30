package com.kghospital.audit.dto;

import com.kghospital.audit.domain.enums.CpoeEventType;

import java.time.Instant;
import java.util.UUID;

public record AuditQueryRequest(
    UUID patientId,
    UUID encounterId,
    CpoeEventType eventType,
    UUID actorId,
    Instant from,
    Instant to,
    int page,
    int size
) {
    public AuditQueryRequest {
        if (size <= 0) size = 50;
        if (size > 200) size = 200;
    }
}
