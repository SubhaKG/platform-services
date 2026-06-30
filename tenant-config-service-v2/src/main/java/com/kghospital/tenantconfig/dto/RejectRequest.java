package com.kghospital.tenantconfig.dto;
import jakarta.validation.constraints.NotBlank;
public record RejectRequest(@NotBlank String reason) {}
