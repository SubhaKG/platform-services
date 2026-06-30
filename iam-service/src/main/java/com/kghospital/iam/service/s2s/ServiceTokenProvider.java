package com.kghospital.iam.service.s2s;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * PLAT-001 §5.3 — TTL-aware S2S JWT cache.
 * Refreshes token 60 seconds before expiry.
 * volatile fields for double-checked locking — safe for concurrent access
 * without synchronized block overhead per spec NFR §8.3.
 */
@Slf4j
@Component
public class ServiceTokenProvider {

    private volatile String cachedToken;
    private volatile Instant expiresAt;

    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.service-account.client-id}")
    private String clientId;

    @Value("${keycloak.service-account.client-secret}")
    private String clientSecret;

    public ServiceTokenProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt.minusSeconds(60))) {
            refresh();
        }
        return cachedToken;
    }

    private synchronized void refresh() {
        // Double-checked locking
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return;
        }

        String tokenUrl = keycloakUrl + "/realms/" + realm +
            "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "client_credentials");
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);

        ResponseEntity<Map> response = restTemplate.exchange(
            tokenUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<?, ?> data = response.getBody();
        cachedToken = (String) data.get("access_token");
        Integer expiresIn = (Integer) data.get("expires_in");
        expiresAt = Instant.now().plusSeconds(expiresIn);

        log.info("S2S token refreshed, expires in {}s", expiresIn);
    }
}
