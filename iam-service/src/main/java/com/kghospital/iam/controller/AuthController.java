package com.kghospital.iam.controller;

import com.kghospital.iam.dto.*;
import com.kghospital.iam.service.auth.AuthService;
import com.kghospital.iam.service.breakglass.BreakGlassService;
import com.kghospital.iam.service.migration.ProgrammaticMigrationRunner;
import com.kghospital.iam.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final BreakGlassService breakGlassService;
    private final ProgrammaticMigrationRunner migrationRunner;

    /** FR-01 — User login, returns JWT with tenant_id + roles */
    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req,
                                HttpServletRequest httpReq) {
        String tenantId = httpReq.getHeader("X-Tenant-ID");
        return authService.login(req, tenantId != null ? tenantId : req.tenantId());
    }

    /** FR-03 — S2S token via client_credentials */
    @PostMapping("/auth/token")
    public S2STokenResponse issueS2SToken(@Valid @RequestBody ClientCredentialsRequest req) {
        return authService.issueS2SToken(req);
    }

    /** FR-07 — Token introspection for downstream S2S validation */
    @PostMapping("/auth/verify")
    @PreAuthorize("hasRole('SERVICE')")
    public UserClaims verify(@Valid @RequestBody TokenIntrospectionRequest req) {
        return authService.introspect(req);
    }

    /** GET /users/me — active user profile scoped to tenant */
    @GetMapping("/users/me")
    public UserContext getUserMe(@AuthenticationPrincipal Jwt jwt) {
        return new UserContext(
            java.util.UUID.fromString(jwt.getSubject()),
            jwt.getClaimAsString("preferred_username"),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("tenant_id"),
            jwt.getClaimAsStringList("roles")
        );
    }

    /** §9.1 — Break-glass: mandatory reason, two-tier audit */
    @PostMapping("/break-glass")
    @PreAuthorize("hasRole('BREAK_GLASS')")
    public BTGTokenResponse breakGlass(@Valid @RequestBody BreakGlassRequest req,
                                        @AuthenticationPrincipal Jwt jwt) {
        String actorId   = jwt.getSubject();
        String actorRole = "ROLE_BREAK_GLASS";
        return breakGlassService.issueBreakGlassToken(req, actorId, actorRole);
    }

    /** §4.4.5 — Admin retry endpoint — no service restart needed */
    @PostMapping("/admin/tenants/{tenantId}/migrate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<String> retryMigration(@PathVariable String tenantId) {
        try {
            migrationRunner.retryTenantMigration(tenantId);
            return ResponseEntity.ok("Tenant released from quarantine: " + tenantId);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                .body("Migration still failing: " + ex.getMessage());
        }
    }

    /** PUT /tenants/{id}/roles — tenant admin role assignment */
    @PutMapping("/tenants/{id}/roles")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<String> updateRoles(@PathVariable String id,
                                               @Valid @RequestBody RoleAssignmentRequest req) {
        // TODO: delegate to Keycloak admin client + PLAT-002 audit
        return ResponseEntity.ok("Roles updated for: " + req.username());
    }

    /** Health check for quarantine status */
    @GetMapping("/admin/quarantine")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> quarantineStatus() {
        // TODO: return TenantQuarantineRegistry status
        return Map.of("message", "Quarantine status endpoint");
    }
}
