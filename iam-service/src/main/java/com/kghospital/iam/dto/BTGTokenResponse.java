package com.kghospital.iam.dto;
public record BTGTokenResponse(String accessToken, Long expiresIn,
    String tokenType, boolean deferredAudit) {}
