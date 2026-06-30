package com.kghospital.audit.dto;

import com.kghospital.audit.domain.enums.ActorRole;
import com.kghospital.audit.domain.enums.AuditAction;
import com.kghospital.audit.domain.enums.CpoeEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AuditEventRequest(
    @NotNull  CpoeEventType eventType,
    @NotNull  UUID actorId,
    @NotNull  ActorRole actorRole,
    @NotNull  UUID patientId,
    UUID encounterId,
    @NotBlank String resourceType,
    @NotNull  UUID resourceId,
    @NotNull  AuditAction action,
    @NotNull  Instant timestamp,
    String idempotencyKey,
    String metadata
) {}
