package com.kghospital.iam.dto;
import java.util.List;
public record UserClaims(String subject, String username, String email,
    String tenantId, List<String> roles, boolean active, long expiresAt) {}
