package com.kghospital.iam.tenant;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PLAT-001 §4.4.4 — Tenant context filter with quarantine check.
 * Resolves tenant ID from X-Tenant-ID header or JWT tenant_id claim.
 * Checks quarantine registry before resolving datasource.
 * Clears ThreadLocal in finally block — ALWAYS.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantQuarantineRegistry quarantineRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
        throws ServletException, java.io.IOException {

        // Skip tenant resolution for public endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/api/v1/auth/login")
            || path.startsWith("/api/v1/auth/token")) {
            chain.doFilter(request, response);
            return;
        }

        String tenantId = resolveTenantId(request);

        if (tenantId == null || tenantId.isBlank()) {
            response.sendError(400, "PLAT-001-E001: X-Tenant-ID missing or unresolvable");
            return;
        }

        // §4.4.4 — quarantine check before datasource access
        if (quarantineRegistry.isQuarantined(tenantId)) {
            response.sendError(503,
                "PLAT-001-E008: Tenant temporarily unavailable — migration pending. Contact support.");
            return;
        }

        TenantContext.setTenantId(tenantId);
        MDC.put("tenantId", tenantId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();   // MUST always clear per §4.2
            MDC.remove("tenantId");
        }
    }

    private String resolveTenantId(HttpServletRequest request) {
        // 1. Check X-Tenant-ID header (primary)
        String header = request.getHeader("X-Tenant-ID");
        if (header != null && !header.isBlank()) return header;

        // 2. Check JWT tenant_id claim (secondary)
        // Spring Security will have decoded JWT by this point
        var auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof
            org.springframework.security.oauth2.jwt.Jwt jwt) {
            String tenantClaim = jwt.getClaimAsString("tenant_id");
            if (tenantClaim != null) return tenantClaim;
        }

        return null;
    }
}
