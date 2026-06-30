package com.kghospital.tenantconfig.dto;
import java.time.Instant;
public record ApprovalResponse(String key, Object effectiveValue, Instant activatedAt) {}
