package com.kghospital.tenantconfig.dto;

import java.time.Instant;
import java.util.UUID;

/** §5.3 FR-12 — chronological change history */
public record ConfigHistoryEntry(
    UUID id, String key, String scope, String scopeContext,
    String oldValue, String newValue, String action,
    UUID actorId, Instant changedAt
) {}
