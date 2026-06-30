package com.kghospital.forms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.forms.domain.entity.FormSchema;
import com.kghospital.forms.dto.FormSchemaRequest;
import com.kghospital.forms.dto.FormSchemaResponse;
import com.kghospital.forms.exception.FormException;
import com.kghospital.forms.repository.FormSchemaRepository;
import com.kghospital.forms.service.fhir.FhirBindingService;
import com.kghospital.forms.tenant.TenantContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PLAT-004 §FR-01, FR-02 — schema definition and legal versioning.
 *
 * "POST /forms/schema returns 201 with schema_id and version; schema
 * retrievable via GET; re-POST with same name increments version."
 *
 * "Published schema versions MUST be immutable — no UPDATE or DELETE
 * operations are permitted on a published version" — enforced via
 * 405 in the controller; this service never exposes an update path.
 */
@Slf4j
@Service
public class FormSchemaService {

    private final FormSchemaRepository schemaRepo;
    private final FhirBindingService fhirBindingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicInteger> versionGaugeCache = new ConcurrentHashMap<>();

    public FormSchemaService(FormSchemaRepository schemaRepo,
                              FhirBindingService fhirBindingService,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.schemaRepo = schemaRepo;
        this.fhirBindingService = fhirBindingService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public FormSchemaResponse createSchema(FormSchemaRequest req) {
        String tenantId = TenantContext.get();

        int nextVersion = req.version() != null
            ? req.version()
            : schemaRepo.findMaxVersion(tenantId, req.name()).map(v -> v + 1).orElse(1);

        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(req.fields());
        } catch (Exception ex) {
            throw FormException.malformedPayload("fields serialization failed");
        }

        // §3.8.2 — validate FHIR mappings at publish time
        JsonNode fieldsNode;
        try {
            fieldsNode = objectMapper.readTree(definitionJson);
        } catch (Exception ex) {
            throw FormException.malformedPayload("invalid fields JSON");
        }
        fhirBindingService.validateMappingsAtPublish(fieldsNode);

        FormSchema schema = FormSchema.builder()
            .tenantId(tenantId)
            .name(req.name())
            .version(nextVersion)
            .definition(definitionJson)
            .isPublished(true)   // §FR-01 — published immediately on create per v1.1 scope
            .build();

        FormSchema saved = schemaRepo.save(schema);
        updateVersionGauge(tenantId, req.name(), nextVersion);

        log.info("Schema published: name={} version={} tenant={}", req.name(), nextVersion, tenantId);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FormSchemaResponse getByIdAndVersion(UUID id, Integer version) {
        FormSchema schema = schemaRepo.findById(id)
            .orElseThrow(() -> FormException.notFound(id));

        // If a specific version was requested but differs from the stored
        // record's version, look up the matching named version explicitly
        if (version != null && !version.equals(schema.getVersion())) {
            schema = schemaRepo.findByTenantIdAndNameAndVersion(
                schema.getTenantId(), schema.getName(), version)
                .orElseThrow(() -> FormException.notFound(id));
        }

        return toResponse(schema);
    }

    @Transactional(readOnly = true)
    public Page<FormSchemaResponse> listVersions(String name, int page, int size) {
        String tenantId = TenantContext.get();
        return schemaRepo.findByTenantIdAndNameOrderByVersionDesc(
            tenantId, name, PageRequest.of(page, Math.min(size, 200))
        ).map(this::toResponse);
    }

    /** Used by FormSubmissionService — FR-04 schema_version validation */
    @Transactional(readOnly = true)
    public FormSchema getPublishedSchemaOrThrow(UUID schemaId, Integer schemaVersion) {
        FormSchema schema = schemaRepo.findByIdAndIsPublishedTrue(schemaId)
            .orElseThrow(() -> FormException.invalidSchemaVersion(schemaId, schemaVersion));

        if (!schema.getVersion().equals(schemaVersion)) {
            throw FormException.invalidSchemaVersion(schemaId, schemaVersion);
        }
        return schema;
    }

    private void updateVersionGauge(String tenantId, String name, int version) {
        String key = tenantId + ":" + name;
        AtomicInteger gaugeValue = versionGaugeCache.computeIfAbsent(key, k -> {
            AtomicInteger v = new AtomicInteger(version);
            Gauge.builder("form_schema_versions_total", v, AtomicInteger::get)
                .tag("tenant", tenantId).tag("schema_name", name)
                .register(meterRegistry);
            return v;
        });
        gaugeValue.set(version);
    }

    private FormSchemaResponse toResponse(FormSchema s) {
        return new FormSchemaResponse(s.getId(), s.getName(), s.getVersion(),
            s.getDefinition(), s.getIsPublished(), s.getCreatedAt());
    }
}
