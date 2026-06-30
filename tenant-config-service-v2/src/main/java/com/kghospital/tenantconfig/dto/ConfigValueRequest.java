package com.kghospital.tenantconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** §7.4 — PUT /config/values */
public record ConfigValueRequest(
    @NotBlank String hospitalId,
    @NotBlank String key,
    @NotNull  Object value,
    @NotBlank String scope,      // hospital | department | role
    String deptId,                // required if scope=department
    String role                   // required if scope=role
) {}
