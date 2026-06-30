package com.kghospital.forms.dto;

import com.kghospital.forms.domain.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** §3.6.1 FormSchemaRequest.fields[] entry */
public record FieldDefinitionDto(
    @NotBlank String fieldId,
    @NotBlank String label,
    @NotNull  FieldType type,
    boolean required,
    EnableWhenCondition enableWhen,
    FhirMapping fhirMapping
) {}
