package com.kghospital.tenantconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** §7.3 — module startup pattern */
public record BatchResolveRequest(
    @NotBlank String hospitalId,
    String deptId,
    String role,
    @NotEmpty List<String> keys
) {}
