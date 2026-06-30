package com.kghospital.tenantconfig.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.tenantconfig.domain.entity.ConfigKeyCatalogue;
import com.kghospital.tenantconfig.domain.entity.ConfigValue;
import com.kghospital.tenantconfig.domain.enums.ConfigValueStatus;
import com.kghospital.tenantconfig.domain.enums.ResolutionScope;
import com.kghospital.tenantconfig.dto.*;
import com.kghospital.tenantconfig.exception.ConfigException;
import com.kghospital.tenantconfig.repository.ConfigKeyCatalogueRepository;
import com.kghospital.tenantconfig.repository.ConfigValueRepository;
import com.kghospital.tenantconfig.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-005 §5.2 — Scope Resolution Engine.
 *
 * FR-06: "The resolution order MUST be: role-scoped value → department-
 * scoped value → hospital-scoped value → platform_default. The first
 * non-null value in this chain is the effective value."
 *
 * FR-10: cached per resolution context with 60s TTL, evicted explicitly
 * on write — see @CacheEvict usage in ConfigValueService.
 *
 * §6.2 Clinical Safety Rule: "If PLAT-005 is unreachable, modules MUST
 * NOT fail clinical operations... If no cache is available, the module
 * MUST use the platform defaults." This rule applies to CALLING modules
 * (out of this service's direct control), but this service still must
 * make platform_default always resolvable even with zero tenant config —
 * which the resolution chain below guarantees by construction.
 */
@Slf4j
@Service
public class ConfigResolutionService {

    private final ConfigKeyCatalogueRepository catalogueRepo;
    private final ConfigValueRepository valueRepo;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public ConfigResolutionService(ConfigKeyCatalogueRepository catalogueRepo,
                                    ConfigValueRepository valueRepo,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this.catalogueRepo = catalogueRepo;
        this.valueRepo = valueRepo;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * §FR-07 — single key resolve.
     * Cacheable per (tenant, key, deptId, role) — §FR-10 60s TTL configured
     * at the cache manager level (see CacheConfig).
     */
    @Cacheable(value = "configResolve", key = "T(String).join(':', #req.key(), #req.hospitalId(), #req.deptId(), #req.role())")
    @Transactional(readOnly = true)
    public ResolveResponse resolve(ResolveRequest req) {
        ConfigKeyCatalogue catalogue = catalogueRepo.findById(req.key())
            .orElseThrow(() -> ConfigException.keyNotFound(req.key()));

        ResolvedValue resolved = resolveChain(req.key(), req.deptId(), req.role(), catalogue);
        recordCacheMetric(true);

        Map<String, String> scopeContext = new LinkedHashMap<>();
        if (req.deptId() != null) scopeContext.put("dept_id", req.deptId());
        if (req.role() != null) scopeContext.put("role", req.role());

        return new ResolveResponse(req.key(), resolved.value(), resolved.resolvedFrom(),
            scopeContext.isEmpty() ? null : scopeContext,
            catalogue.getDataType().name(), catalogue.getClinicalImpact());
    }

    /**
     * §FR-08 — batch resolve, the primary module startup pattern.
     * "Modules load their full config once, not key by key."
     */
    @Transactional(readOnly = true)
    public BatchResolveResponse resolveBatch(BatchResolveRequest req) {
        Map<String, BatchResolveResponse.ResolvedEntry> result = new LinkedHashMap<>();

        for (String key : req.keys()) {
            try {
                ConfigKeyCatalogue catalogue = catalogueRepo.findById(key)
                    .orElseThrow(() -> ConfigException.keyNotFound(key));
                ResolvedValue resolved = resolveChain(key, req.deptId(), req.role(), catalogue);
                result.put(key, new BatchResolveResponse.ResolvedEntry(
                    resolved.value(), resolved.resolvedFrom()));
            } catch (ConfigException ex) {
                log.warn("Batch resolve: key not found, skipping: {}", key);
                // Per FR-08 acceptance criteria the whole batch should still
                // succeed for resolvable keys; unresolvable keys are simply
                // omitted rather than failing the entire batch (207-style
                // partial success, though spec response code table shows
                // 200/207 — we return 200 with omission, since the spec's
                // example response doesn't show an error shape per-key).
            }
        }

        return new BatchResolveResponse(result);
    }

    /**
     * Core resolution chain per FR-06.
     * Tries role → department → hospital → platform_default in order,
     * returning the first ACTIVE (not PENDING/REJECTED) value found.
     */
    private ResolvedValue resolveChain(String key, String deptId, String role,
                                        ConfigKeyCatalogue catalogue) {
        String tenantId = TenantContext.get();

        if (role != null) {
            Optional<ConfigValue> roleValue = valueRepo.findActiveValue(
                tenantId, key, ResolutionScope.role, jsonContext("role", role));
            if (roleValue.isPresent()) {
                return new ResolvedValue(parseValue(roleValue.get().getValue()), "role");
            }
        }

        if (deptId != null) {
            Optional<ConfigValue> deptValue = valueRepo.findActiveValue(
                tenantId, key, ResolutionScope.department, jsonContext("dept_id", deptId));
            if (deptValue.isPresent()) {
                return new ResolvedValue(parseValue(deptValue.get().getValue()), "department");
            }
        }

        Optional<ConfigValue> hospitalValue = valueRepo.findActiveValue(
            tenantId, key, ResolutionScope.hospital, null);
        if (hospitalValue.isPresent()) {
            return new ResolvedValue(parseValue(hospitalValue.get().getValue()), "hospital");
        }

        return new ResolvedValue(parseValue(catalogue.getPlatformDefault()), "platform_default");
    }

    private String jsonContext(String field, String value) {
        try {
            return objectMapper.writeValueAsString(Map.of(field, value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Object parseValue(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private void recordCacheMetric(boolean hit) {
        String key = hit ? "hit" : "miss";
        counterCache.computeIfAbsent("config_cache_" + key, k ->
            Counter.builder("config_resolve_cache_total").tag("result", key).register(meterRegistry)
        ).increment();
    }

    private record ResolvedValue(Object value, String resolvedFrom) {}
}
