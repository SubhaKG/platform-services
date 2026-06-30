package com.kghospital.integrationregistry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.integrationregistry.domain.entity.AdapterEntry;
import com.kghospital.integrationregistry.domain.enums.AdapterStatus;
import com.kghospital.integrationregistry.dto.*;
import com.kghospital.integrationregistry.exception.AdapterException;
import com.kghospital.integrationregistry.kafka.AdapterEventProducer;
import com.kghospital.integrationregistry.repository.AdapterEntryRepository;
import com.kghospital.integrationregistry.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-012 §6.1, §6.3, §6.4 — registry CRUD, the cached lookup endpoint,
 * and the adapter health state machine.
 *
 * FR-12 — "ACTIVE → DEGRADED after 3 consecutive failures. DEGRADED →
 * SUSPENDED after 10 consecutive failures. SUSPENDED → ACTIVE ONLY via
 * an explicit admin action (not automatic recovery)."
 */
@Slf4j
@Service
public class AdapterRegistryService {

    private static final int DEGRADED_THRESHOLD = 3;
    private static final int SUSPENDED_THRESHOLD = 10;

    private final AdapterEntryRepository entryRepo;
    private final AdapterCatalogueService catalogueService;
    private final AuditForwardingService auditForwardingService;
    private final AlertDispatchService alertDispatchService;
    private final AdapterEventProducer eventProducer;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public AdapterRegistryService(AdapterEntryRepository entryRepo,
                                  AdapterCatalogueService catalogueService,
                                  AuditForwardingService auditForwardingService,
                                  AlertDispatchService alertDispatchService,
                                  AdapterEventProducer eventProducer,
                                  CacheManager cacheManager,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.entryRepo = entryRepo;
        this.catalogueService = catalogueService;
        this.auditForwardingService = auditForwardingService;
        this.alertDispatchService = alertDispatchService;
        this.eventProducer = eventProducer;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /** §FR-01 — register a new adapter entry */
    @Transactional
    public AdapterEntryResponse register(AdapterEntryRequest req, UUID actorId) {
        String tenantId = TenantContext.get();

        catalogueService.validateAdapterType(req.adapterId(), req.adapterType().name());

        if (entryRepo.findByTenantIdAndAdapterId(tenantId, req.adapterId()).isPresent()) {
            throw AdapterException.duplicateEntry(tenantId, req.adapterId());
        }

        try {
            AdapterEntry entry = AdapterEntry.builder()
                .tenantId(tenantId)
                .adapterId(req.adapterId())
                .adapterType(req.adapterType())
                .endpoint(req.endpoint())
                .protocol(req.protocol())
                .syncFrequency(req.syncFrequency())
                .timeoutMs(req.timeoutMs() != null ? req.timeoutMs() : 5000)
                .retryPolicy(objectMapper.writeValueAsString(req.retryPolicy()))
                .config(req.config() != null ? objectMapper.writeValueAsString(req.config()) : null)
                .status(AdapterStatus.ACTIVE)
                .updatedBy(actorId)
                .build();

            AdapterEntry saved = entryRepo.save(entry);
            auditForwardingService.forwardMutation(tenantId, req.adapterId(), "create", null,
                objectMapper.writeValueAsString(req), actorId);

            log.info("Adapter registered: tenant={} adapterId={} type={}",
                tenantId, req.adapterId(), req.adapterType());
            return toResponse(saved);

        } catch (AdapterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AdapterException.malformedEntry("serialization failed: " + ex.getMessage());
        }
    }

    /** §FR-08 — the primary Integration Engine lookup. Cached, §FR-09 60s TTL. */
    @Cacheable(value = "adapterLookup", key = "T(String).join(':', #hospitalId, #adapterId)")
    @Transactional(readOnly = true)
    public AdapterEntryResponse lookup(String hospitalId, String adapterId) {
        AdapterEntry entry = entryRepo.findByTenantIdAndAdapterId(hospitalId, adapterId)
            .orElseThrow(() -> AdapterException.entryNotFound(hospitalId, adapterId));
        recordCacheMetric("hit");
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public Page<AdapterEntryResponse> listForHospital(String hospitalId, int page, int size) {
        return entryRepo.findByTenantId(hospitalId, PageRequest.of(page, size)).map(this::toResponse);
    }

    @Transactional
    public AdapterEntryResponse update(UUID id, AdapterUpdateRequest req, UUID actorId) {
        String tenantId = TenantContext.get();
        AdapterEntry entry = entryRepo.findById(id)
            .orElseThrow(() -> AdapterException.entryNotFound(tenantId, "id=" + id));

        if (!entry.getTenantId().equals(tenantId)) {
            throw AdapterException.insufficientRoleOrCrossHospital();
        }

        try {
            String beforeJson = objectMapper.writeValueAsString(entry);

            if (req.endpoint() != null) entry.setEndpoint(req.endpoint());
            if (req.syncFrequency() != null) entry.setSyncFrequency(req.syncFrequency());
            if (req.timeoutMs() != null) entry.setTimeoutMs(req.timeoutMs());
            if (req.retryPolicy() != null) entry.setRetryPolicy(objectMapper.writeValueAsString(req.retryPolicy()));
            if (req.config() != null) entry.setConfig(objectMapper.writeValueAsString(req.config()));
            entry.setUpdatedAt(Instant.now());
            entry.setUpdatedBy(actorId);

            AdapterEntry saved = entryRepo.save(entry);
            String afterJson = objectMapper.writeValueAsString(saved);

            evictCache(tenantId, entry.getAdapterId());
            eventProducer.publishConfigUpdated(tenantId, entry.getAdapterId());
            auditForwardingService.forwardMutation(tenantId, entry.getAdapterId(), "update",
                beforeJson, afterJson, actorId);

            log.info("Adapter updated: tenant={} adapterId={}", tenantId, entry.getAdapterId());
            return toResponse(saved);

        } catch (Exception ex) {
            throw AdapterException.malformedEntry("serialization failed: " + ex.getMessage());
        }
    }

    /** §8.1 DELETE — soft delete (deactivate) */
    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        String tenantId = TenantContext.get();
        AdapterEntry entry = entryRepo.findById(id)
            .orElseThrow(() -> AdapterException.entryNotFound(tenantId, "id=" + id));

        if (!entry.getTenantId().equals(tenantId)) {
            throw AdapterException.insufficientRoleOrCrossHospital();
        }

        entry.setStatus(AdapterStatus.INACTIVE);
        entry.setUpdatedAt(Instant.now());
        entry.setUpdatedBy(actorId);
        entryRepo.save(entry);

        evictCache(tenantId, entry.getAdapterId());
        auditForwardingService.forwardMutation(tenantId, entry.getAdapterId(), "delete", null, null, actorId);
        log.info("Adapter deactivated: tenant={} adapterId={}", tenantId, entry.getAdapterId());
    }

    /**
     * §FR-11, FR-12 — the Integration Engine reports call outcome.
     * Drives the automatic ACTIVE → DEGRADED → SUSPENDED state machine.
     * SUSPENDED → ACTIVE is explicitly excluded here — only reachable
     * via recoverManually().
     */
    @Transactional
    public void reportHealth(HealthReportRequest req) {
        AdapterEntry entry = entryRepo.findByTenantIdAndAdapterId(req.hospitalId(), req.adapterId())
            .orElseThrow(() -> AdapterException.entryNotFound(req.hospitalId(), req.adapterId()));

        entry.setLastPing(Instant.now());
        String previousStatus = entry.getStatus().name();

        if (req.success()) {
            entry.setLastSuccess(Instant.now());
            entry.setConsecutiveFailures(0);
            entry.setLastError(null);
            entry.setDegradedSince(null);
            // Per FR-12, automatic recovery from DEGRADED on success IS implied by
            // "consecutive_failures resets to 0 on any success" — but SUSPENDED
            // explicitly requires manual admin action, never automatic. DEGRADED
            // is not explicitly exempted from auto-recovery in the spec text, so
            // a success after DEGRADED (but before SUSPENDED) naturally clears it.
            if (entry.getStatus() == AdapterStatus.DEGRADED) {
                entry.setStatus(AdapterStatus.ACTIVE);
                log.info("Adapter auto-recovered DEGRADED→ACTIVE on success: {}", req.adapterId());
            }
        } else {
            entry.setConsecutiveFailures(entry.getConsecutiveFailures() + 1);
            entry.setLastError(req.errorMessage());

            int failures = entry.getConsecutiveFailures();

            if (failures >= SUSPENDED_THRESHOLD && entry.getStatus() != AdapterStatus.SUSPENDED) {
                entry.setStatus(AdapterStatus.SUSPENDED);
                eventProducer.publishSuspended(entry);
                alertDispatchService.dispatchStateTransitionAlert(entry, "SUSPENDED");
                log.warn("Adapter SUSPENDED: tenant={} adapter={} failures={}",
                    req.hospitalId(), req.adapterId(), failures);

            } else if (failures >= DEGRADED_THRESHOLD && entry.getStatus() == AdapterStatus.ACTIVE) {
                entry.setStatus(AdapterStatus.DEGRADED);
                entry.setDegradedSince(Instant.now());
                eventProducer.publishDegraded(entry, previousStatus);
                alertDispatchService.dispatchStateTransitionAlert(entry, "DEGRADED");
                log.warn("Adapter DEGRADED: tenant={} adapter={} failures={}",
                    req.hospitalId(), req.adapterId(), failures);
            }
        }

        entryRepo.save(entry);
        incrementHealthCounter(req.hospitalId(), req.adapterId(), req.success());
    }

    /** §FR-12 — "SUSPENDED → ACTIVE only via an explicit admin action" */
    @Transactional
    public AdapterEntryResponse recoverManually(UUID id, UUID actorId) {
        AdapterEntry entry = entryRepo.findById(id)
            .orElseThrow(() -> AdapterException.entryNotFound("", "id=" + id));

        if (entry.getStatus() != AdapterStatus.SUSPENDED) {
            throw AdapterException.malformedEntry("adapter is not SUSPENDED: " + id);
        }

        entry.setStatus(AdapterStatus.ACTIVE);
        entry.setConsecutiveFailures(0);
        entry.setDegradedSince(null);
        entry.setLastError(null);
        entry.setUpdatedBy(actorId);
        AdapterEntry saved = entryRepo.save(entry);

        evictCache(entry.getTenantId(), entry.getAdapterId());
        eventProducer.publishRecovered(saved);
        auditForwardingService.forwardMutation(entry.getTenantId(), entry.getAdapterId(),
            "update", "SUSPENDED", "ACTIVE", actorId);

        log.info("Adapter manually recovered: id={} actor={}", id, actorId);
        return toResponse(saved);
    }

    /** §FR-14 — health dashboard summary */
    @Transactional(readOnly = true)
    public java.util.List<HealthSummaryEntry> healthSummary(String hospitalId) {
        return entryRepo.findByTenantId(hospitalId, PageRequest.of(0, 200)).getContent().stream()
            .map(e -> new HealthSummaryEntry(e.getAdapterId(), e.getAdapterType().name(),
                e.getStatus().name(), e.getLastSuccess(), e.getConsecutiveFailures(), e.getDegradedSince()))
            .toList();
    }

    // ── Credential operations ───────────────────────────────────────────────

    @Transactional
    public CredentialSubmissionResponse setCredentialRef(UUID entryId, String newRef, UUID actorId) {
        AdapterEntry entry = entryRepo.findById(entryId)
            .orElseThrow(() -> AdapterException.entryNotFound("", "id=" + entryId));

        entry.setCredentialRef(newRef);
        entry.setUpdatedAt(Instant.now());
        entry.setUpdatedBy(actorId);
        entryRepo.save(entry);

        evictCache(entry.getTenantId(), entry.getAdapterId());
        eventProducer.publishCredentialRotated(entry.getTenantId(), entry.getAdapterId(), newRef);
        auditForwardingService.forwardCredentialRotation(entry.getTenantId(), entry.getAdapterId(), newRef, actorId);

        return new CredentialSubmissionResponse(newRef, Instant.now().toString());
    }

    public AdapterEntry getEntryById(UUID id) {
        return entryRepo.findById(id)
            .orElseThrow(() -> AdapterException.entryNotFound("", "id=" + id));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void evictCache(String tenantId, String adapterId) {
        var cache = cacheManager.getCache("adapterLookup");
        if (cache != null) cache.evict(tenantId + ":" + adapterId);
    }

    private void recordCacheMetric(String result) {
        counterCache.computeIfAbsent("adapter_lookup_cache_" + result, k ->
            Counter.builder("adapter_lookup_cache_total").tag("result", result).register(meterRegistry)
        ).increment();
    }

    private void incrementHealthCounter(String tenant, String adapterId, boolean success) {
        String key = tenant + ":" + adapterId + ":" + success;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder("adapter_health_reports_total")
                .tag("tenant", tenant).tag("adapter_id", adapterId)
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
        ).increment();
    }

    private AdapterEntryResponse toResponse(AdapterEntry e) {
        return new AdapterEntryResponse(e.getId(), e.getTenantId(), e.getAdapterId(),
            e.getAdapterType().name(), e.getStatus().name(), e.getEndpoint(), e.getProtocol().name(),
            e.getSyncFrequency(), e.getTimeoutMs(), e.getRetryPolicy(), e.getCredentialRef(), e.getConfig(),
            new AdapterEntryResponse.HealthBlock(e.getLastPing(), e.getLastSuccess(),
                e.getConsecutiveFailures(), e.getDegradedSince()),
            e.getUpdatedAt());
    }
}
