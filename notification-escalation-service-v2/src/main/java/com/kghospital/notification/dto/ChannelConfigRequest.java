package com.kghospital.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** PLAT-003 §3.6.1 PUT /config/channels — raw JSON body matching §3.6.4 schema */
public record ChannelConfigRequest(@NotBlank String configJson) {}
