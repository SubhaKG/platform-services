package com.kghospital.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** PLAT-003 §3.6.3 — inbound dispatch event schema */
public record AlertDispatchRequest(
    @NotBlank String alertType,
    @NotBlank String hospitalId,
    @NotBlank String recipientRule,
    @NotNull  Map<String, Object> context
) {}
