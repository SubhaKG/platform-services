package com.kghospital.integrationregistry.domain.enums;

/**
 * PLAT-012 §6.4 FR-12 — lifecycle states with explicit transition rules.
 * ACTIVE → DEGRADED at 3 consecutive failures.
 * DEGRADED → SUSPENDED at 10 consecutive failures.
 * SUSPENDED → ACTIVE ONLY via explicit admin action — never automatic.
 */
public enum AdapterStatus {
    ACTIVE, INACTIVE, DEGRADED, SUSPENDED
}
