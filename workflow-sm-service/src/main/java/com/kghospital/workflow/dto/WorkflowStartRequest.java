package com.kghospital.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record WorkflowStartRequest(
    @NotBlank String entityType,
    @NotNull  UUID entityId
) {}
