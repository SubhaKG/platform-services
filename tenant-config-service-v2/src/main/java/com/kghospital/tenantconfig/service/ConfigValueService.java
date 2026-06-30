package com.kghospital.tenantconfig.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.tenantconfig.domain.entity.ConfigAuditLog;
import com.kghospital.tenantconfig.domain.entity.ConfigKeyCatalogue;
import com.kghospital.tenantconfig.domain.entity.ConfigValue;
import com.kghospital.tenantconfig.domain.enums.ConfigValueStatus;
import com.kghospital.tenantconfig.domain.enums.ResolutionScope;
import com.kghospital.tenantconfig.dto.*;
import com.kghospital.tenantconfig.exception.ConfigException;
import com.kghospital.tenantconfig.repository.ConfigAuditLogRepository;
import com.kghospital.tenantconfig.repository.ConfigValueRepository;
import com.kghospital.tenantconfig.service.validation.ConfigValueValidator;
import com.kghospital.tenantconfig.kafka.ConfigEventProducer;
import com.kghospital.tenantconfig.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * PLAT-005 §5.2, §5.3 — config value writes, clinical sign-off workflow,
 * and audit trail.
 *
 * FR-03: keys with requires_clinical_sign_off=true are staged PENDING
 * until a ROLE_CLINICAL_LEAD approves — the write does NOT affect the
 * resolved value until approval.
 * FR-10: cache evicted explicitly on every write — "no module restart
 * required."
 * FR-11: every write (create/update/approve/reject/delete) recorded
 * immutably, forwarded to PLAT-002 fire-and-forget.
 */
@Slf4j
@Service
public class ConfigValueService {

    private final ConfigValueRepository valueRepo;
    private final ConfigAuditLogRepository auditRepo;
    private final CatalogueService catalogueService;
    private final ConfigValueValidator validator;
    private final AuditForwardingService auditForwardingService;
    private final CacheManager cacheManager;
    private final ConfigEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    public ConfigValueService(ConfigValueRepository valueRepo,
                              ConfigAuditLogRepository auditRepo,
                              CatalogueService catalogueService,
                              ConfigValueValidator validator,
                              AuditForwardingService auditForwardingService,
                              CacheManager cacheManager,
                              ConfigEventProducer eventProducer,
                              ObjectMapper objectMapper) {
        this.valueRepo = valueRepo;
        this.auditRepo = auditRepo;
        this.catalogueService = catalogueService;
        this.validator = validator;
        this.auditForwardingService = auditForwardingService;
        this.cacheManager = cacheManager;
        this.eventProducer = eventProducer;
        this.objectMapper = objectMapper;
    }

    /**
     * §FR-05, FR-09, FR-03 — set a config value. Returns either an
     * immediately-active ConfigValueResponse or a PendingApprovalResponse
     * if clinical sign-off is required.
     */
    @Transactional
    public Object setValue(ConfigValueRequest req, UUID actorId) {
        String tenantId = TenantContext.get();
        ConfigKeyCatalogue catalogue = catalogueService.getByKeyOrThrow(req.key());

        ResolutionScope scope = ResolutionScope.valueOf(req.scope());
        validator.validateScopeSupported(catalogue, req.scope());
        validator.validate(catalogue, req.value());

        String scopeContextJson = buildScopeContext(scope, req.deptId(), req.role());

        try {
            String valueJson = objectMapper.writeValueAsString(req.value());

            String oldValueJson = valueRepo
                .findByTenantIdAndConfigKeyAndScopeAndScopeContext(tenantId, req.key(), scope, scopeContextJson)
                .map(ConfigValue::getValue).orElse(null);

            boolean needsSignOff = catalogue.getRequiresClinicalSignOff();

            ConfigValue value = ConfigValue.builder()
                .tenantId(tenantId)
                .configKey(req.key())
                .scope(scope)
                .scopeContext(scopeContextJson)
                .value(valueJson)
                .status(needsSignOff ? ConfigValueStatus.PENDING_CLINICAL_APPROVAL : ConfigValueStatus.ACTIVE)
                .setBy(actorId)
                .activatedAt(needsSignOff ? null : Instant.now())
                .build();

            ConfigValue saved = valueRepo.save(value);

            recordAudit(saved.getId(), req.key(), catalogue.getModule(), req.scope(),
                scopeContextJson, oldValueJson, valueJson, "create", actorId, tenantId);

            if (needsSignOff) {
                Object currentEffective = resolveCurrentEffective(req.key(), catalogue, scope, scopeContextJson);
                evictCache();
                log.info("Config staged PENDING_CLINICAL_APPROVAL: key={} id={}", req.key(), saved.getId());
                return new PendingApprovalResponse(saved.getId().toString(),
                    "PENDING_CLINICAL_APPROVAL",
                    "This key requires approval from a ROLE_CLINICAL_LEAD before it takes effect.",
                    currentEffective);
            }

            evictCache();
            eventProducer.publish(tenantId, req.key(), scope, oldValueJson, valueJson, actorId);
            log.info("Config value set: key={} scope={} id={}", req.key(), req.scope(), saved.getId());
            return toResponse(saved);

        } catch (Exception ex) {
            if (ex instanceof ConfigException) throw (ConfigException) ex;
            throw ConfigException.malformedRequest("value serialization failed: " + ex.getMessage());
        }
    }

    /** §FR-03, API §7.4 — clinical lead approves a PENDING value */
    @Transactional
    public ApprovalResponse approve(UUID id, UUID actorId) {
        ConfigValue value = valueRepo.findById(id)
            .orElseThrow(() -> ConfigException.malformedRequest("config value not found: " + id));

        if (value.getStatus() != ConfigValueStatus.PENDING_CLINICAL_APPROVAL) {
            throw ConfigException.malformedRequest("value is not pending approval: " + id);
        }

        value.setStatus(ConfigValueStatus.ACTIVE);
        value.setApprovedBy(actorId);
        value.setActivatedAt(Instant.now());
        ConfigValue saved = valueRepo.save(value);

        recordAudit(saved.getId(), saved.getConfigKey(), null, saved.getScope().name(),
            saved.getScopeContext(), null, saved.getValue(), "approve", actorId, saved.getTenantId());

        evictCache();
        log.info("Config approved: id={} actor={}", id, actorId);

        Object resolvedValue;
        try {
            resolvedValue = objectMapper.readValue(saved.getValue(), Object.class);
        } catch (Exception ex) {
            resolvedValue = saved.getValue();
        }
        return new ApprovalResponse(saved.getConfigKey(), resolvedValue, saved.getActivatedAt());
    }

    /** §7.1 — clinical lead rejects a PENDING value */
    @Transactional
    public void reject(UUID id, String reason, UUID actorId) {
        ConfigValue value = valueRepo.findById(id)
            .orElseThrow(() -> ConfigException.malformedRequest("config value not found: " + id));

        value.setStatus(ConfigValueStatus.REJECTED);
        value.setRejectionReason(reason);
        ConfigValue saved = valueRepo.save(value);

        recordAudit(saved.getId(), saved.getConfigKey(), null, saved.getScope().name(),
            saved.getScopeContext(), null, saved.getValue(), "reject", actorId, saved.getTenantId());

        evictCache();
        log.info("Config rejected: id={} reason={}", id, reason);
    }

    /** §7.1 — DELETE /config/values/{id} */
    @Transactional
    public void deleteValue(UUID id, UUID actorId) {
        ConfigValue value = valueRepo.findById(id)
            .orElseThrow(() -> ConfigException.malformedRequest("config value not found: " + id));

        recordAudit(value.getId(), value.getConfigKey(), null, value.getScope().name(),
            value.getScopeContext(), value.getValue(), null, "delete", actorId, value.getTenantId());

        valueRepo.delete(value);
        evictCache();
        log.info("Config value deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public Page<ConfigValueResponse> listValues(String hospitalId, String module, int page, int size) {
        return valueRepo.findByTenantId(hospitalId, PageRequest.of(page, size))
            .map(this::toResponse);
    }

    private Object resolveCurrentEffective(String key, ConfigKeyCatalogue catalogue,
                                            ResolutionScope scope, String scopeContext) {
        // The pre-existing effective value, before this PENDING write applies
        try {
            return objectMapper.readValue(catalogue.getPlatformDefault(), Object.class);
        } catch (Exception ex) {
            return catalogue.getPlatformDefault();
        }
    }

    private String buildScopeContext(ResolutionScope scope, String deptId, String role) {
        try {
            if (scope == ResolutionScope.department && deptId != null) {
                return objectMapper.writeValueAsString(Map.of("dept_id", deptId));
            }
            if (scope == ResolutionScope.role && role != null) {
                return objectMapper.writeValueAsString(Map.of("role", role));
            }
            return null; // hospital scope
        } catch (Exception ex) {
            return null;
        }
    }

    private void recordAudit(UUID valueId, String key, String module, String scope,
                              String scopeContext, String oldValue, String newValue,
                              String action, UUID actorId, String tenantId) {
        ConfigAuditLog auditLog = ConfigAuditLog.builder()
            .tenantId(tenantId)
            .configKey(key)
            .module(module != null ? module : "")
            .scope(scope)
            .scopeContext(scopeContext)
            .oldValue(oldValue)
            .newValue(newValue)
            .action(action)
            .actorId(actorId)
            .build();
        ConfigAuditLog saved = auditRepo.save(auditLog);
        auditForwardingService.forwardFireAndForget(saved, tenantId);
    }

    private void evictCache() {
        var cache = cacheManager.getCache("configResolve");
        if (cache != null) cache.clear();
    }

    private ConfigValueResponse toResponse(ConfigValue v) {
        Object value, scopeContext = null;
        try {
            value = objectMapper.readValue(v.getValue(), Object.class);
            if (v.getScopeContext() != null) {
                scopeContext = objectMapper.readValue(v.getScopeContext(), Object.class);
            }
        } catch (Exception ex) {
            value = v.getValue();
        }
        return new ConfigValueResponse(v.getId(), v.getConfigKey(), v.getScope().name(),
            scopeContext, value, v.getStatus().name(), v.getSetBy(), v.getApprovedBy(),
            v.getActivatedAt(), v.getCreatedAt());
    }
}
