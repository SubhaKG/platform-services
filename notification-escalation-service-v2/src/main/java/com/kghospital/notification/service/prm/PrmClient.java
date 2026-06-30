package com.kghospital.notification.service.prm;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PLAT-003 §3.8 — PRM Service integration.
 * "Circuit breaker; if unavailable, alert queued as PENDING_RESOLUTION for retry."
 *
 * Resolves named recipient rules (admitting_physician, duty_consultant, HOD,
 * ward_nurse) to concrete user IDs at runtime. Exact PRM contract is Open
 * Issue #2 in spec — this client uses a reasonable assumed contract:
 * GET /api/v1/prm/resolve?rule={rule}&hospitalId={id}&context={...}
 */
@Slf4j
@Component
public class PrmClient {

    private final RestTemplate restTemplate;

    @Value("${services.prm.base-url:http://prm-service:8200}")
    private String prmBaseUrl;

    public PrmClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * §FR-02 — resolve recipient_rule to concrete user IDs.
     * Throws on PRM unavailability — caller queues as PENDING_RESOLUTION.
     * Returns empty list if PRM responds but finds no match — caller
     * marks alert UNRESOLVABLE per spec (these are two distinct outcomes).
     */
    @CircuitBreaker(name = "prm-service")
    @SuppressWarnings("unchecked")
    public List<UUID> resolveRecipients(String recipientRule, String tenantId,
                                         Map<String, Object> context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", tenantId);

        Map<String, Object> body = Map.of(
            "rule", recipientRule,
            "hospitalId", tenantId,
            "context", context
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            prmBaseUrl + "/api/v1/prm/resolve",
            HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("PRM resolution failed for rule: " + recipientRule);
        }

        List<String> userIdStrings = (List<String>) response.getBody().getOrDefault("userIds", List.of());
        return userIdStrings.stream().map(UUID::fromString).toList();
    }
}
