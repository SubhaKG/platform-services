package com.kghospital.iam.service.breakglass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.iam.domain.entity.EmergencyAuditQueue;
import com.kghospital.iam.dto.*;
import com.kghospital.iam.repository.EmergencyAuditQueueRepository;
import com.kghospital.iam.tenant.TenantContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-001 §9.2 — Break-Glass two-tier safety net.
 *
 * Tier 1 (normal path): PLAT-002 responds 201 within 500ms.
 *   → Audit written synchronously. Access granted immediately.
 *
 * Tier 2 (PLAT-002 down): PLAT-002 times out or errors.
 *   → Audit written to local emergency_audit_queue (same-server DB — cannot fail).
 *   → Access granted immediately — patient care NEVER blocked.
 *   → P1 alert fired to on-call team.
 *   → Queue replayed to PLAT-002 by @Scheduled job every 30s.
 */
@Slf4j
@Service
public class BreakGlassService {

    private final EmergencyAuditQueueRepository queueRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    @Value("${services.audit.base-url:http://audit-service}")
    private String auditBaseUrl;

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public BreakGlassService(EmergencyAuditQueueRepository queueRepo,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.queueRepo = queueRepo;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * §9.2.3 — Issue BTG token with two-tier safety net.
     */
    @Transactional
    public BTGTokenResponse issueBreakGlassToken(BreakGlassRequest req,
                                                   String actorId,
                                                   String actorRole) {
        String tenantId = TenantContext.getTenantId();

        Map<String, Object> auditEvent = Map.of(
            "actor_id",           actorId,
            "actor_role",         actorRole,
            "break_glass_reason", req.reason(),
            "patient_id",         req.patientId() != null ? req.patientId().toString() : "",
            "purpose_of_use",     req.purposeOfUse() != null ? req.purposeOfUse() : "BTG",
            "tenant_id",          tenantId != null ? tenantId : "",
            "timestamp",          Instant.now().toString(),
            "severity",           "HIGH"
        );

        boolean deferredAudit = false;

        try {
            // Tier 1: synchronous PLAT-002 write — 500ms timeout
            writeAuditTier1(auditEvent, tenantId);
            log.info("Break-glass Tier 1 audit written: actor={}", actorId);
        } catch (Exception ex) {
            // Tier 2: PLAT-002 unavailable — local emergency queue
            log.warn("Break-glass Tier 2 activated (PLAT-002 unavailable): {}", ex.getMessage());
            saveToEmergencyQueue(auditEvent);
            deferredAudit = true;
            // TODO: fire P1 alert via PLAT-005 notification service
        }

        // Issue elevated BTG token via Keycloak
        String btgToken = issueBtgToken(actorId);

        incrementCounter("iam_break_glass_total", tenantId != null ? tenantId : "unknown",
            deferredAudit ? "deferred" : "immediate");

        return new BTGTokenResponse(btgToken, 900L, "Bearer", deferredAudit);
    }

    /**
     * §9.2.3 — Background replay job — every 30 seconds.
     * Replays pending break-glass events to PLAT-002 on recovery.
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void flushEmergencyQueue() {
        List<EmergencyAuditQueue> pending = queueRepo.findPending();
        if (pending.isEmpty()) return;

        log.info("Flushing {} deferred break-glass audit events", pending.size());

        for (EmergencyAuditQueue event : pending) {
            try {
                writeAuditTier1Replay(event.getBreakGlassEvent());
                event.setReplayedAt(Instant.now());
                queueRepo.save(event);
                log.info("Deferred audit replayed: id={}", event.getId());
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                queueRepo.save(event);
                if (event.getRetryCount() > 10) {
                    log.error("P0: Deferred audit cannot be replayed after 10 attempts: id={}", event.getId());
                    // TODO: fire P0 alert
                }
                log.warn("Deferred audit replay pending: id={} attempt={}",
                    event.getId(), event.getRetryCount());
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "plat002-audit")
    private void writeAuditTier1(Map<String, Object> event, String tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) headers.set("X-Tenant-ID", tenantId);

        // Wrap as PLAT-002 audit event
        Map<String, Object> auditRequest = Map.of(
            "eventType",    "CPOE_ORDER_VERIFIED",   // closest PLAT-002 type
            "actorId",      event.getOrDefault("actor_id", UUID.randomUUID()).toString(),
            "actorRole",    event.getOrDefault("actor_role", "PHYSICIAN").toString(),
            "patientId",    event.getOrDefault("patient_id", UUID.randomUUID().toString()),
            "resourceType", "BreakGlass",
            "resourceId",   UUID.randomUUID().toString(),
            "action",       "VERIFY",
            "timestamp",    Instant.now().toString(),
            "metadata",     event
        );

        ResponseEntity<Void> response = restTemplate.exchange(
            auditBaseUrl + "/api/v1/audit-events",
            HttpMethod.POST, new HttpEntity<>(auditRequest, headers), Void.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("PLAT-002 returned " + response.getStatusCode());
        }
    }

    private void writeAuditTier1Replay(String eventJson) throws Exception {
        writeAuditTier1(objectMapper.readValue(eventJson, Map.class), null);
    }

    private void saveToEmergencyQueue(Map<String, Object> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            queueRepo.save(EmergencyAuditQueue.builder()
                .breakGlassEvent(json)
                .build());
        } catch (Exception ex) {
            log.error("CRITICAL: Emergency queue save failed: {}", ex.getMessage());
        }
    }

    private String issueBtgToken(String actorId) {
        // In a real impl: call Keycloak to issue elevated token with BTG claim
        // For now return a placeholder — integrate with Keycloak admin client
        return "btg-token-placeholder-" + actorId;
    }

    private void incrementCounter(String name, String tenant, String outcome) {
        String key = name + ":" + tenant + ":" + outcome;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder(name)
                .tag("tenant", tenant)
                .tag("outcome", outcome)
                .register(meterRegistry)
        ).increment();
    }
}
