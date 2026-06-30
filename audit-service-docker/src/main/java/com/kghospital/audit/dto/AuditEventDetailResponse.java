package com.kghospital.audit.dto;

import com.kghospital.audit.domain.enums.ActorRole;
import com.kghospital.audit.domain.enums.AuditAction;
import com.kghospital.audit.domain.enums.CpoeEventType;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDetailResponse(
    UUID id,
    CpoeEventType eventType,
    UUID actorId,
    ActorRole actorRole,
    UUID patientId,
    UUID encounterId,
    String resourceType,
    UUID resourceId,
    AuditAction action,
    Instant timestamp,
    String idempotencyKey,
    String metadata,
    Instant createdAt
) {}
