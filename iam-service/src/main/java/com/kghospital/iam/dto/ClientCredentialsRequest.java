package com.kghospital.iam.dto;
import jakarta.validation.constraints.NotBlank;
public record ClientCredentialsRequest(@NotBlank String clientId, @NotBlank String clientSecret) {}
