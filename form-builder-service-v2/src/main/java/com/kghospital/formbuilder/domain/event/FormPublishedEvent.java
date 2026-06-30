package com.kghospital.formbuilder.domain.event;

import com.kghospital.formbuilder.domain.enums.FormCategory;

import java.time.Instant;
import java.util.UUID;

public record FormPublishedEvent(
    UUID formId,
    String formName,
    FormCategory category,
    Integer version,
    String tenantId,
    Instant publishedAt
) {}
