package com.kghospital.iam.dto;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** §9.2.1 — mandatory reason field + patient context */
public record BreakGlassRequest(
    @NotBlank String reason,
    UUID patientId,
    String purposeOfUse    // HL7 BTG code
) {}
