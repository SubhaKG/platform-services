package com.kghospital.formbuilder.dto;

import com.kghospital.formbuilder.domain.enums.FormCategory;
import com.kghospital.formbuilder.domain.enums.FormStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FormDefinitionResponse(
    UUID id,
    String name,
    String description,
    FormCategory category,
    FormStatus status,
    Integer version,
    String tenantId,
    List<FieldDefinitionDto> fields,
    String conditionalRules,
    String metadata,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt
) {}
