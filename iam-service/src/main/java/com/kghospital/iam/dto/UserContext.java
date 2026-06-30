package com.kghospital.iam.dto;
import java.util.List;
import java.util.UUID;
public record UserContext(UUID id, String username, String email,
    String tenantId, List<String> roles) {}
