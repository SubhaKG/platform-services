package com.kghospital.tenantconfig.dto;

import jakarta.validation.constraints.NotBlank;

/** §7.2 — GET /config/resolve params */
public record ResolveRequest(
    @NotBlank String key,
    @NotBlank String hospitalId,
    String deptId,
    String role
) {}
