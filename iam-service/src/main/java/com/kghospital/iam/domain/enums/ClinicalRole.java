package com.kghospital.iam.domain.enums;

/**
 * PLAT-001 §7 — CPOE clinical role mapping.
 * Roles embedded in JWT and govern CPOE-Specs-V2 permissions.
 */
public enum ClinicalRole {
    ROLE_DOCTOR,           // Physician — order entry, prescriptions
    ROLE_NURSE,            // Ward Nurse — administration, IV infusions
    ROLE_LAB_TECH,         // Lab Technician — specimen, diagnostic reports
    ROLE_RADIOLOGIST,      // Radiologist — imaging, PACS
    ROLE_TENANT_ADMIN,     // Tenant Admin — staff management, BLOCKED from patient data
    ROLE_SERVICE,          // Service Account — S2S calls only
    ROLE_BREAK_GLASS,      // Break-Glass — emergency override
    ROLE_PLATFORM_ADMIN    // Platform Admin — migration retry, infra ops
}
