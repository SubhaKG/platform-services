package com.kghospital.tenantconfig.controller;

import com.kghospital.tenantconfig.dto.*;
import com.kghospital.tenantconfig.repository.ConfigAuditLogRepository;
import com.kghospital.tenantconfig.domain.entity.ConfigAuditLog;
import com.kghospital.tenantconfig.service.CatalogueService;
import com.kghospital.tenantconfig.service.ConfigResolutionService;
import com.kghospital.tenantconfig.service.ConfigValueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class TenantConfigController {

    private final ConfigResolutionService resolutionService;
    private final ConfigValueService valueService;
    private final CatalogueService catalogueService;
    private final ConfigAuditLogRepository auditLogRepo;

    /** §FR-07 — single key resolve. Any authenticated service may read. */
    @GetMapping("/resolve")
    public ResolveResponse resolve(
        @RequestParam String key,
        @RequestParam String hospitalId,
        @RequestParam(required = false) String deptId,
        @RequestParam(required = false) String role
    ) {
        return resolutionService.resolve(new ResolveRequest(key, hospitalId, deptId, role));
    }

    /** §FR-08 — batch resolve, the module startup pattern */
    @PostMapping("/resolve/batch")
    public BatchResolveResponse resolveBatch(@Valid @RequestBody BatchResolveRequest req) {
        return resolutionService.resolveBatch(req);
    }

    /** §6.4 — admin view of all configured values for a hospital */
    @GetMapping("/values")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public Page<ConfigValueResponse> listValues(
        @RequestParam String hospitalId,
        @RequestParam(required = false) String module,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return valueService.listValues(hospitalId, module, page, size);
    }

    /** §FR-05, FR-09, FR-03 — set a value; 202 if clinical sign-off pending */
    @PutMapping("/values")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Object> setValue(@Valid @RequestBody ConfigValueRequest req,
                                            Principal principal) {
        UUID actorId = UUID.fromString(principal.getName());
        Object result = valueService.setValue(req, actorId);
        HttpStatus status = result instanceof PendingApprovalResponse
            ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @DeleteMapping("/values/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public void deleteValue(@PathVariable UUID id, Principal principal) {
        valueService.deleteValue(id, UUID.fromString(principal.getName()));
    }

    /** §FR-03 — clinical lead approves a PENDING value */
    @PostMapping("/values/{id}/approve")
    @PreAuthorize("hasRole('CLINICAL_LEAD')")
    public ApprovalResponse approve(@PathVariable UUID id, Principal principal) {
        return valueService.approve(id, UUID.fromString(principal.getName()));
    }

    @PostMapping("/values/{id}/reject")
    @PreAuthorize("hasRole('CLINICAL_LEAD')")
    public void reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req,
                       Principal principal) {
        valueService.reject(id, req.reason(), UUID.fromString(principal.getName()));
    }

    /** §FR-12 — chronological change history */
    @GetMapping("/history")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public Page<ConfigHistoryEntry> history(
        @RequestParam String key,
        @RequestParam String hospitalId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return auditLogRepo.findByTenantIdAndConfigKeyOrderByChangedAtAsc(
            hospitalId, key, PageRequest.of(page, size)
        ).map(this::toHistoryEntry);
    }

    /** §FR-01 — register a key. Platform Core only. */
    @PostMapping("/catalogue")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CatalogueKeyResponse registerKey(@Valid @RequestBody CatalogueKeyRequest req) {
        return catalogueService.registerKey(req);
    }

    /** §FR-04 — browse the key catalogue */
    @GetMapping("/catalogue")
    public Page<CatalogueKeyResponse> browseCatalogue(
        @RequestParam(required = false) String module,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return catalogueService.browse(module, status, page, size);
    }

    private ConfigHistoryEntry toHistoryEntry(ConfigAuditLog a) {
        return new ConfigHistoryEntry(a.getId(), a.getConfigKey(), a.getScope(),
            a.getScopeContext(), a.getOldValue(), a.getNewValue(), a.getAction(),
            a.getActorId(), a.getChangedAt());
    }
}
