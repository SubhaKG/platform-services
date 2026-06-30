package com.kghospital.integrationregistry.controller;

import com.kghospital.integrationregistry.dto.*;
import com.kghospital.integrationregistry.service.AdapterCatalogueService;
import com.kghospital.integrationregistry.service.AdapterRegistryService;
import com.kghospital.integrationregistry.service.CredentialSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdapterRegistryController {

    private final AdapterRegistryService registryService;
    private final CredentialSubmissionService credentialService;
    private final AdapterCatalogueService catalogueService;

    /** §FR-08 — primary Integration Engine lookup. ROLE_INTEGRATION_ENGINE only per §7.3. */
    @GetMapping("/adapters")
    @PreAuthorize("hasRole('INTEGRATION_ENGINE') or hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public Object lookupOrList(
        @RequestParam String hospitalId,
        @RequestParam(required = false) String adapterId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        if (adapterId != null) {
            return registryService.lookup(hospitalId, adapterId);
        }
        return registryService.listForHospital(hospitalId, page, size);
    }

    /** §FR-01 — register a new adapter entry */
    @PostMapping("/adapters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public AdapterEntryResponse register(@Valid @RequestBody AdapterEntryRequest req, Principal principal) {
        return registryService.register(req, UUID.fromString(principal.getName()));
    }

    @PutMapping("/adapters/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public AdapterEntryResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody AdapterUpdateRequest req,
                                       Principal principal) {
        return registryService.update(id, req, UUID.fromString(principal.getName()));
    }

    @DeleteMapping("/adapters/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public void deactivate(@PathVariable UUID id, Principal principal) {
        registryService.deactivate(id, UUID.fromString(principal.getName()));
    }

    /** §FR-05, FR-06 — submit or rotate credentials */
    @PostMapping("/adapters/{id}/credentials")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public CredentialSubmissionResponse submitCredential(
        @PathVariable UUID id,
        @Valid @RequestBody CredentialSubmissionRequest req,
        Principal principal
    ) {
        return credentialService.submitCredential(id, req, UUID.fromString(principal.getName()));
    }

    /** §FR-11 — Integration Engine reports call outcome. ROLE_INTEGRATION_ENGINE only. */
    @PostMapping("/adapters/{id}/health-report")
    @PreAuthorize("hasRole('INTEGRATION_ENGINE')")
    public void healthReport(@PathVariable UUID id, @Valid @RequestBody HealthReportRequest req) {
        registryService.reportHealth(req);
    }

    /** §6.4 FR-12 — admin-only manual recovery from SUSPENDED */
    @PostMapping("/adapters/{id}/recover")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public AdapterEntryResponse recover(@PathVariable UUID id, Principal principal) {
        return registryService.recoverManually(id, UUID.fromString(principal.getName()));
    }

    /** §FR-14 — health dashboard summary */
    @GetMapping("/adapters/health")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public List<HealthSummaryEntry> healthSummary(@RequestParam String hospitalId) {
        return registryService.healthSummary(hospitalId);
    }

    /** §FR-02 — browse the platform-shared adapter catalogue */
    @GetMapping("/adapter-catalogue")
    public List<AdapterCatalogueEntryResponse> browseCatalogue() {
        return catalogueService.listAll();
    }

    /** §FR-02 — register new adapter_id. ROLE_PLATFORM_ADMIN only. */
    @PostMapping("/adapter-catalogue/adapters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public AdapterCatalogueEntryResponse registerAdapterId(@Valid @RequestBody AdapterCatalogueEntryRequest req) {
        return catalogueService.registerAdapterId(req);
    }
}
