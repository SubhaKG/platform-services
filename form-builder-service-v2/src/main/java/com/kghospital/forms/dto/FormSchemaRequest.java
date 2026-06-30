package com.kghospital.forms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** §3.6.1 POST /forms/schema — version auto-incremented if omitted */
public record FormSchemaRequest(
    @NotBlank String name,
    Integer version,
    @NotEmpty @Valid List<FieldDefinitionDto> fields
) {}
