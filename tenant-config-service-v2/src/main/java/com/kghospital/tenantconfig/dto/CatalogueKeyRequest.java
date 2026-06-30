package com.kghospital.tenantconfig.dto;

import com.kghospital.tenantconfig.domain.enums.ConfigDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** §5.1 FR-02 — POST /config/catalogue */
public record CatalogueKeyRequest(
    @NotBlank String key,
    @NotBlank String module,
    @NotNull  ConfigDataType dataType,
    List<Object> allowedValues,     // mutually exclusive with allowedRange
    AllowedRange allowedRange,
    @NotNull  Object platformDefault,
    @NotNull  List<String> supportedScopes,
    String description,
    boolean clinicalImpact,
    boolean requiresClinicalSignOff
) {
    public record AllowedRange(Number min, Number max) {}
}
