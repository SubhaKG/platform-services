package com.kghospital.integrationregistry.iam;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * PLAT-012 §10 — "Auth Service (PLAT-001) — REST (sync) — Circuit
 * breaker; JWT failure returns 503." Matches the platform-wide pattern.
 */
@Slf4j
@Component
public class IamIntrospectionClient {

    private final RestTemplate restTemplate;

    @Value("${services.iam.base-url:http://iam-service:8084}")
    private String iamBaseUrl;

    public IamIntrospectionClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "iam-introspection")
    public IntrospectionResult introspect(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
            iamBaseUrl + "/api/v1/auth/verify",
            HttpMethod.POST, new HttpEntity<>(Map.of("token", token), headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("PLAT-001-E003: Invalid or expired JWT");
        }

        Map<?, ?> claims = response.getBody();
        return new IntrospectionResult(
            (String) claims.get("subject"),
            (String) claims.get("username"),
            (String) claims.get("tenantId"),
            (List<String>) claims.get("roles"),
            Boolean.TRUE.equals(claims.get("active"))
        );
    }

    public record IntrospectionResult(
        String subject, String username, String tenantId,
        List<String> roles, boolean active
    ) {}
}
