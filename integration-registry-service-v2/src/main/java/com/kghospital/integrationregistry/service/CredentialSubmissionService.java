package com.kghospital.integrationregistry.service;

import com.kghospital.integrationregistry.dto.CredentialSubmissionRequest;
import com.kghospital.integrationregistry.dto.CredentialSubmissionResponse;
import com.kghospital.integrationregistry.exception.AdapterException;
import com.kghospital.integrationregistry.secrets.SecretsManagerClient;
import com.kghospital.integrationregistry.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PLAT-012 §6.2 FR-05, FR-06 — credential submission and rotation.
 *
 * "The submitted credential is forwarded DIRECTLY to the secrets
 * manager; the returned credential_ref is stored in the registry
 * entry. The credential value is never held in PLAT-012 memory beyond
 * the forwarding call." This service is the only place
 * CredentialSubmissionRequest.credentialValue() is ever read — it's
 * consumed by submit() and never touches any other code path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialSubmissionService {

    private final SecretsManagerClient secretsManagerClient;
    private final AdapterRegistryService registryService;

    @Transactional
    public CredentialSubmissionResponse submitCredential(UUID entryId,
                                                          CredentialSubmissionRequest req,
                                                          UUID actorId) {
        var entry = registryService.getEntryById(entryId);
        String tenantId = TenantContext.get();

        if (!entry.getTenantId().equals(tenantId)) {
            throw AdapterException.insufficientRoleOrCrossHospital();
        }

        String newRef;
        try {
            // §FR-06 — new VERSION, not overwrite — old ref remains valid
            // until Integration Engine's own cache TTL expires
            newRef = secretsManagerClient.submit(tenantId, entry.getAdapterId(),
                req.credentialValue(), req.credentialType());
        } catch (SecretsManagerClient.SecretsManagerUnavailableException ex) {
            throw AdapterException.secretsManagerUnavailable();
        }

        return registryService.setCredentialRef(entryId, newRef, actorId);
    }
}
