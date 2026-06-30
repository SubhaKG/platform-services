package com.kghospital.integrationregistry.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * PLAT-012 §10 — "Audit Service (PLAT-002) — Fire-and-forget; failure
 * does not block registry operations." Same pattern as
 * tenant-config-service's AuditForwardingService.
 *
 * §6.1 FR-04 — "Credential rotation events MUST be audited WITHOUT
 * logging the credential value." The forwardCredentialRotation method
 * below takes only the credentialRef, never the actual value — there
 * is structurally no parameter through which a secret could leak here.
 */
@Slf4j
@Service
public class AuditForwardingService {

    private final RestTemplate restTemplate;

    @Value("${services.audit.base-url:http://audit-service:8081}")
    private String auditBaseUrl;

    public AuditForwardingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void forwardMutation(String tenantId, String adapterId, String action,
                                String beforeState, String afterState, UUID actorId) {
        forward(tenantId, adapterId, action, Map.of(
            "before", beforeState != null ? beforeState : "", "after", afterState != null ? afterState : ""
        ), actorId);
    }

    /** §FR-04 — credential value is NEVER passed to this method */
    public void forwardCredentialRotation(String tenantId, String adapterId,
                                          String newCredentialRef, UUID actorId) {
        forward(tenantId, adapterId, "credential_rotate",
            Map.of("newCredentialRef", newCredentialRef), actorId);
    }

    private void forward(String tenantId, String adapterId, String action,
                         Map<String, Object> metadata, UUID actorId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId);

            Map<String, Object> body = Map.of(
                "eventType",    "CPOE_ORDER_MODIFIED",
                "actorId",      actorId.toString(),
                "actorRole",    "PLATFORM_INTEGRATION",
                "patientId",    UUID.randomUUID().toString(),
                "resourceType", "AdapterEntry",
                "resourceId",   adapterId,
                "action",       action.toUpperCase(),
                "timestamp",    Instant.now().toString(),
                "metadata",     metadata
            );

            restTemplate.exchange(auditBaseUrl + "/api/v1/audit-events",
                HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);

        } catch (Exception ex) {
            log.warn("PLAT-002 audit forward failed (non-blocking) for adapter={}: {}",
                adapterId, ex.getMessage());
        }
    }
}
