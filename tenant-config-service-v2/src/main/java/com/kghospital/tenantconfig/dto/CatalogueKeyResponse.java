package com.kghospital.tenantconfig.dto;

public record CatalogueKeyResponse(
    String key, String module, String dataType, String allowedValues,
    String allowedRange, String platformDefault, String supportedScopes,
    String description, boolean clinicalImpact, boolean requiresClinicalSignOff,
    String status, String replacementKey
) {}
