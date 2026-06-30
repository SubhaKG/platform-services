package com.kghospital.tenantconfig.service;

import com.kghospital.tenantconfig.domain.entity.ConfigAuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * PLAT-005 §9 — "Audit Service (PLAT-002) — Fire-and-forget; PLAT-002
 * failure does not block config writes, but is flagged as a warning."
 *
 * UNLIKE PLAT-002 forwarding in audit-service/workflow-sm-service/
 * forms-service (where the caller's own write is REJECTED on audit
 * failure), this service's writes are NOT blocked by audit failure —
 * the local ConfigAuditLog row is always the durable source of truth,
 * and PLAT-002 forwarding is best-effort.
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

    public void forwardFireAndForget(ConfigAuditLog log, String tenantId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId);

            Map<String, Object> body = Map.of(
                "eventType",    "CPOE_ORDER_MODIFIED",
                "actorId",      log.getActorId().toString(),
                "actorRole",    "PLATFORM_CONFIG",
                "patientId",    java.util.UUID.randomUUID().toString(), // N/A for config; PLAT-002 requires a value
                "resourceType", "ConfigValue",
                "resourceId",   log.getId().toString(),
                "action",       log.getAction().toUpperCase(),
                "timestamp",    log.getChangedAt().toString(),
                "metadata",     Map.of(
                    "key", log.getConfigKey(), "module", log.getModule(),
                    "scope", log.getScope(), "oldValue", String.valueOf(log.getOldValue()),
                    "newValue", String.valueOf(log.getNewValue())
                )
            );

            restTemplate.exchange(auditBaseUrl + "/api/v1/audit-events",
                HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);

        } catch (Exception ex) {
            // §9 — failure does not block config writes; flagged as warning only
            this.log.warn("PLAT-002 audit forward failed (non-blocking) for config={}: {}",
                log.getId(), ex.getMessage());
        }
    }
}
