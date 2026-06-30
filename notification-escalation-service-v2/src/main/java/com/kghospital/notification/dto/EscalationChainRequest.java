package com.kghospital.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** PLAT-003 §3.6.1 PUT /config/escalation-chains — raw JSON matching §3.6.5 schema */
public record EscalationChainRequest(
    @NotBlank String alertType,
    @NotBlank String chainJson
) {}
