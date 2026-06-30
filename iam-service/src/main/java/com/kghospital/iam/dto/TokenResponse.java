package com.kghospital.iam.dto;
import java.util.List;
public record TokenResponse(String accessToken, String refreshToken,
    Long expiresIn, String tokenType, String tenantId, List<String> roles) {}
