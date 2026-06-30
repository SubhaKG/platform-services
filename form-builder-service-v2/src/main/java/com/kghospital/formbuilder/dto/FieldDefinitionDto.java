package com.kghospital.formbuilder.dto;

import com.kghospital.formbuilder.domain.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FieldDefinitionDto(
    UUID id,
    @NotBlank String fieldKey,
    @NotBlank String label,
    @NotNull FieldType type,
    @NotNull Integer position,
    Boolean required,
    String validation,
    String defaultValue,
    String options,
    String lookupConfig,
    String placeholder,
    String helpText,
    String section,
    String visibleCondition
) {}
