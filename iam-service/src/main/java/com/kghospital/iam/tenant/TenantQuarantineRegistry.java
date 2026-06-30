package com.kghospital.iam.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-001 §4.4.3 — In-memory quarantine registry.
 * Rebuilt on each startup. Quarantined tenants return 503.
 * Released via POST /admin/tenants/{id}/migrate on successful retry.
 */
@Slf4j
@Component
public class TenantQuarantineRegistry {

    private final Set<String> quarantinedTenants = ConcurrentHashMap.newKeySet();
    private final Map<String, String> failureReasons = new ConcurrentHashMap<>();

    public void markFailed(String tenantId, String reason) {
        quarantinedTenants.add(tenantId);
        failureReasons.put(tenantId, reason);
        log.error("Tenant quarantined: {} reason={}", tenantId, reason);
    }

    public boolean isQuarantined(String tenantId) {
        return quarantinedTenants.contains(tenantId);
    }

    public void release(String tenantId) {
        quarantinedTenants.remove(tenantId);
        failureReasons.remove(tenantId);
        log.info("Tenant released from quarantine: {}", tenantId);
    }

    public Set<String> getQuarantinedTenants() {
        return Set.copyOf(quarantinedTenants);
    }

    public String getFailureReason(String tenantId) {
        return failureReasons.getOrDefault(tenantId, "Unknown");
    }
}
