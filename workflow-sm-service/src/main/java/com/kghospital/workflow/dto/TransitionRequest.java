package com.kghospital.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record TransitionRequest(
    @NotBlank String actionCode,
    String actorSubject,
    String actorRole,
    String comment,
    UUID correlationId
) {}
