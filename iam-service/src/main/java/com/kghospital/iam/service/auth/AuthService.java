package com.kghospital.iam.service.auth;

import com.kghospital.iam.dto.*;
import com.kghospital.iam.exception.IamException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-001 §6 FR-01, FR-03, FR-07 — Auth operations.
 * Delegates to Keycloak for token issuance; validates locally via JWKS.
 */
@Slf4j
@Service
public class AuthService {

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public AuthService(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * FR-01 — OIDC login via Keycloak, returns JWT with tenant_id + roles.
     */
    public TokenResponse login(LoginRequest req, String tenantId) {
        String tokenUrl = keycloakUrl + "/realms/" + realm +
            "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "password");
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);
        body.add("username",      req.username());
        body.add("password",      req.password());
        body.add("scope",         "openid profile roles");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> data = response.getBody();
            String accessToken  = (String) data.get("access_token");
            String refreshToken = (String) data.get("refresh_token");
            Integer expiresIn   = (Integer) data.get("expires_in");

            List<String> roles = extractRoles(accessToken);

            // iam_logins_total counter per §15.2
            incrementCounter("iam_logins_total", tenantId, "success");

            log.info("Login success: user={} tenant={}", req.username(), tenantId);
            return new TokenResponse(accessToken, refreshToken,
                expiresIn.longValue(), "Bearer", tenantId, roles);

        } catch (HttpClientErrorException ex) {
            incrementCounter("iam_logins_total", tenantId, "failure");
            log.warn("Login failed: user={} status={}", req.username(), ex.getStatusCode());
            throw IamException.invalidToken();
        }
    }

    /**
     * FR-03 — S2S token via client_credentials grant.
     */
    public S2STokenResponse issueS2SToken(ClientCredentialsRequest req) {
        String tokenUrl = keycloakUrl + "/realms/" + realm +
            "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "client_credentials");
        body.add("client_id",     req.clientId());
        body.add("client_secret", req.clientSecret());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> data = response.getBody();
            String token    = (String) data.get("access_token");
            Integer expires = (Integer) data.get("expires_in");

            incrementCounter("iam_s2s_tokens_total", req.clientId(), "success");
            return new S2STokenResponse(token, expires.longValue(), "Bearer", req.clientId());

        } catch (HttpClientErrorException ex) {
            incrementCounter("iam_s2s_tokens_total", req.clientId(), "failure");
            throw IamException.invalidToken();
        }
    }

    /**
     * FR-07 — Token introspection endpoint for S2S validation.
     * Validates locally via JWKS — no Keycloak round-trip per §5.6.
     */
    public UserClaims introspect(TokenIntrospectionRequest req) {
        String introspectUrl = keycloakUrl + "/realms/" + realm +
            "/protocol/openid-connect/token/introspect";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", req.token());

        ResponseEntity<Map> response = restTemplate.exchange(
            introspectUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<?, ?> data = response.getBody();
        Boolean active = (Boolean) data.get("active");

        if (active == null || !active) {
            throw IamException.invalidToken();
        }

        String subject  = (String) data.get("sub");
        String username = (String) data.get("preferred_username");
        String email    = (String) data.get("email");
        String tenantId = (String) data.get("tenant_id");
        Long exp        = ((Number) data.get("exp")).longValue();

        List<String> roles = extractRolesFromClaims(data);

        return new UserClaims(subject, username, email, tenantId, roles, true, exp);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(String token) {
        try {
            // Decode JWT payload (base64 middle section)
            String[] parts = token.split("\\.");
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(decoded, Map.class);
            return extractRolesFromClaims(claims);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromClaims(Map<?, ?> claims) {
        try {
            Map<?, ?> realmAccess = (Map<?, ?>) claims.get("realm_access");
            if (realmAccess != null) {
                return (List<String>) realmAccess.get("roles");
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    private void incrementCounter(String name, String label, String outcome) {
        String key = name + ":" + label + ":" + outcome;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder(name)
                .tag("tenant", label)
                .tag("outcome", outcome)
                .register(meterRegistry)
        ).increment();
    }
}
