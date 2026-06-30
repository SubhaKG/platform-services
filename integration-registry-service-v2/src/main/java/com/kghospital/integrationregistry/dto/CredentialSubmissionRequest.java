package com.kghospital.integrationregistry.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * §6.2 FR-05 — POST /adapters/{id}/credentials.
 * "The credential value is never held in PLAT-012 memory beyond the
 * forwarding call." This record exists only transiently in the
 * request body and the forwarding call to the secrets manager —
 * never persisted, never logged, never echoed back.
 */
public record CredentialSubmissionRequest(
    @NotBlank String credentialValue,
    String credentialType   // api_key | bearer_token | oauth_client_secret | mtls_cert
) {}
