package com.kghospital.forms.dto;

import java.time.Instant;
import java.util.UUID;

public record FormSchemaResponse(
    UUID id, String name, Integer version,
    String definition, Boolean isPublished, Instant createdAt
) {}
