package com.kghospital.tenantconfig.dto;

import java.time.Instant;
import java.util.UUID;

public record ConfigValueResponse(
    UUID id, String key, String scope, Object scopeContext,
    Object value, String status, UUID setBy, UUID approvedBy,
    Instant activatedAt, Instant createdAt
) {}
