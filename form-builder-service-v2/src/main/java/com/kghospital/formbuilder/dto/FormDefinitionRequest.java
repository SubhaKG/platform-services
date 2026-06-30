package com.kghospital.formbuilder.dto;

import com.kghospital.formbuilder.domain.enums.FormCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FormDefinitionRequest(
    @NotBlank String name,
    String description,
    @NotNull FormCategory category,
    @Valid List<FieldDefinitionDto> fields,
    String conditionalRules,
    String metadata
) {}
