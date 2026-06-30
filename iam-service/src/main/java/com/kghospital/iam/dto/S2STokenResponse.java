package com.kghospital.iam.dto;
public record S2STokenResponse(String accessToken, Long expiresIn, String tokenType, String serviceName) {}
