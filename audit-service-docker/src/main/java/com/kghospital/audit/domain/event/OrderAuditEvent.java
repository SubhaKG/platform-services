package com.kghospital.audit.domain.event;

import com.kghospital.audit.domain.enums.ActorRole;
import com.kghospital.audit.domain.enums.OrderAction;
import com.kghospital.audit.domain.enums.OrderType;

import java.time.Instant;
import java.util.UUID;

public record OrderAuditEvent(
    String eventId,
    String eventType,
    UUID orderId,
    UUID patientId,
    UUID encounterId,
    OrderType orderType,
    OrderAction action,
    UUID actorId,
    String actorName,
    ActorRole actorRole,
    String ipAddress,
    String beforeSnapshot,
    String afterSnapshot,
    String changeReason,
    UUID correlationId,
    Instant occurredAt
) {}
