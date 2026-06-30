package com.kghospital.iam.dto;
import jakarta.validation.constraints.NotBlank;
public record TokenIntrospectionRequest(@NotBlank String token) {}
