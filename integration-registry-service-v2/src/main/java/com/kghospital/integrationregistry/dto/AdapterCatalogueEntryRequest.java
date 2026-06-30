package com.kghospital.integrationregistry.dto;

import com.kghospital.integrationregistry.domain.enums.Category;
import com.kghospital.integrationregistry.domain.enums.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** §6.1 FR-02 — POST /adapter-catalogue/adapters, ROLE_PLATFORM_ADMIN only */
public record AdapterCatalogueEntryRequest(
    @NotBlank String adapterId,
    @NotNull  Category category,
    @NotNull  Direction direction,
    String description,
    @NotEmpty List<String> supportedTypes
) {}
