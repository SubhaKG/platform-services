package com.kghospital.tenantconfig.domain.enums;

/**
 * PLAT-005 §2, §5.2 FR-06 — resolution hierarchy.
 * role → department → hospital → platform_default (most specific wins).
 */
public enum ResolutionScope {
    role, department, hospital, platform_default
}
