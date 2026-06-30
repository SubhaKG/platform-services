package com.kghospital.integrationregistry.secrets;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * PLAT-012 §6.2 SECURITY REQUIREMENT — "Credentials MUST NEVER be
 * stored in the adapter registry database... not even for test
 * environments." This client forwards the credential directly to an
 * external secrets manager and returns ONLY a reference key.
 *
 * §12 Open Issue #1 — secrets manager choice (AWS Secrets Manager vs
 * HashiCorp Vault vs Kubernetes Secrets) is undecided platform-wide.
 * This client targets a generic HTTP secrets manager API behind
 * ${services.secrets-manager.base-url} so swapping the actual backend
 * later requires no change to this service's code — just the URL and
 * auth config.
 *
 * The credential value passes through this class's stack frame only —
 * it is never assigned to an instance field, never logged (note: no
 * log line below references req.credentialValue()), and never
 * returned in any response.
 */
@Slf4j
@Component
public class SecretsManagerClient {

    private final RestTemplate restTemplate;

    @Value("${services.secrets-manager.base-url:http://secrets-manager:8200}")
    private String secretsManagerBaseUrl;

    public SecretsManagerClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Submits a credential and returns its reference key.
     * §FR-06 — "writes it to the secrets manager under a NEW version" —
     * rotation creates a new version, never overwrites in place, so
     * in-flight Integration Engine calls using the old ref are
     * unaffected until they refresh on next TTL expiry.
     */
    @CircuitBreaker(name = "secrets-manager")
    public String submit(String tenantId, String adapterId, String credentialValue,
                         String credentialType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "path", tenantId + "/" + adapterId + "/" + (credentialType != null ? credentialType : "default"),
            "value", credentialValue
        );

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                secretsManagerBaseUrl + "/v1/secrets",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Secrets manager returned " + response.getStatusCode());
            }

            return (String) response.getBody().get("ref");

        } catch (Exception ex) {
            log.error("Secrets manager submission failed for tenant={} adapter={}: {}",
                tenantId, adapterId, ex.getMessage());
            // §8.3 PLAT-012-E007 — caller must resubmit in full; nothing was stored
            throw new SecretsManagerUnavailableException(ex);
        }
    }

    public static class SecretsManagerUnavailableException extends RuntimeException {
        public SecretsManagerUnavailableException(Throwable cause) { super(cause); }
    }
}
