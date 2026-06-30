package com.kghospital.tenantconfig.dto;

/** §7.4 — 202 Accepted response when clinical sign-off is required */
public record PendingApprovalResponse(
    String pendingId, String status, String message, Object currentEffectiveValue
) {}
