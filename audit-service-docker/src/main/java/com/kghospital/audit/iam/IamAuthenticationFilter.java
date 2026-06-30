package com.kghospital.audit.iam;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * PLAT-002 §3.5.1 — replaces local JWKS decode with synchronous PLAT-001
 * introspection call. On introspection failure (PLAT-001 down or invalid
 * token), responds 503 per spec §3.8 integration table — NOT 401 — because
 * the spec explicitly says "Return 503 if JWT introspection fails;
 * circuit breaker active."
 */
@Slf4j
public class IamAuthenticationFilter extends OncePerRequestFilter {

    private final IamIntrospectionClient iamClient;

    public IamAuthenticationFilter(IamIntrospectionClient iamClient) {
        this.iamClient = iamClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(401, "PLAT-002-E002: Missing or invalid JWT");
            return;
        }

        String token = authHeader.substring(7);

        try {
            IamIntrospectionClient.IntrospectionResult result = iamClient.introspect(token);

            if (!result.active()) {
                response.sendError(401, "PLAT-002-E002: Token is not active");
                return;
            }

            List<GrantedAuthority> authorities = result.roles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

            var auth = new UsernamePasswordAuthenticationToken(
                result.subject(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);

        } catch (Exception ex) {
            // Per spec §3.8: introspection failure → 503, circuit breaker active
            log.error("IAM introspection failed: {}", ex.getMessage());
            response.sendError(503, "PLAT-002-E007: PLAT-001 unavailable — introspection failed");
        }
    }
}
