package com.kghospital.forms.service;

import com.kghospital.forms.domain.entity.FormSubmission;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * PLAT-004 §FR-06, §3.9 — "Submission MUST be rejected if audit write
 * fails — no silent data loss permitted." Synchronous forward to
 * PLAT-002 with actor_id, schema_version, tenant context.
 */
@Slf4j
@Service
public class AuditWriteService {

    private final RestTemplate restTemplate;

    @Value("${services.audit.base-url:http://audit-service:8081}")
    private String auditBaseUrl;

    public AuditWriteService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "plat002-audit")
    public void writeSubmissionAudit(FormSubmission submission, String tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", tenantId);

        Map<String, Object> body = Map.of(
            "eventType",    "CPOE_ORDER_MODIFIED",   // closest PLAT-002 v1.0 whitelisted type
            "actorId",      submission.getActorId().toString(),
            "actorRole",    submission.getActorRole(),
            "patientId",    submission.getPatientId().toString(),
            "resourceType", "FormSubmission",
            "resourceId",   submission.getId().toString(),
            "action",       "CREATE",
            "timestamp",    submission.getTimestamp().toString(),
            "metadata",     Map.of(
                "schemaId",      submission.getSchemaId().toString(),
                "schemaVersion", submission.getSchemaVersion(),
                "encounterId",   submission.getEncounterId() != null
                                 ? submission.getEncounterId().toString() : ""
            )
        );

        ResponseEntity<Void> response = restTemplate.exchange(
            auditBaseUrl + "/api/v1/audit-events",
            HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("PLAT-002 returned " + response.getStatusCode());
        }
    }
}
