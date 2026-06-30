package com.kghospital.forms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.forms.domain.entity.FormSchema;
import com.kghospital.forms.domain.entity.FormSubmission;
import com.kghospital.forms.dto.FormSubmissionRequest;
import com.kghospital.forms.dto.FormSubmissionResponse;
import com.kghospital.forms.dto.SubmissionQueryRequest;
import com.kghospital.forms.exception.FormException;
import com.kghospital.forms.repository.FormSubmissionRepository;
import com.kghospital.forms.service.fhir.FhirBindingService;
import com.kghospital.forms.service.validation.EnableWhenEvaluator;
import com.kghospital.forms.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-004 §FR-03–08 — submission validation, FHIR mapping, audit forwarding.
 *
 * Submission flow:
 * 1. FR-04 — validate schema_id + schema_version exist and are published
 * 2. FR-03 — evaluate enableWhen; reject 422 on violation
 * 3. FR-05 — map to FHIR resource; reject 422 on invalid binding
 * 4. FR-08 — idempotency check (24h dedup)
 * 5. Persist submission
 * 6. FR-06 — forward to PLAT-002 synchronously; REJECT submission if audit write fails
 */
@Slf4j
@Service
public class FormSubmissionService {

    private final FormSubmissionRepository submissionRepo;
    private final FormSchemaService schemaService;
    private final EnableWhenEvaluator enableWhenEvaluator;
    private final FhirBindingService fhirBindingService;
    private final AuditWriteService auditWriteService;
    private final ObjectMapper objectMapper;
    private final com.kghospital.forms.kafka.FormSubmissionEventProducer eventProducer;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public FormSubmissionService(FormSubmissionRepository submissionRepo,
                                  FormSchemaService schemaService,
                                  EnableWhenEvaluator enableWhenEvaluator,
                                  FhirBindingService fhirBindingService,
                                  AuditWriteService auditWriteService,
                                  com.kghospital.forms.kafka.FormSubmissionEventProducer eventProducer,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.submissionRepo = submissionRepo;
        this.schemaService = schemaService;
        this.enableWhenEvaluator = enableWhenEvaluator;
        this.fhirBindingService = fhirBindingService;
        this.auditWriteService = auditWriteService;
        this.eventProducer = eventProducer;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public IdempotencyResult submit(FormSubmissionRequest req) {
        String tenantId = TenantContext.get();

        // §FR-08 — idempotency check
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            var existing = submissionRepo.findByIdempotencyKeyWithin24h(req.idempotencyKey(), cutoff);
            if (existing.isPresent()) {
                log.info("Idempotent duplicate submission: key={}", req.idempotencyKey());
                return new IdempotencyResult(toResponse(existing.get()), false);
            }
        }

        // §FR-04 — schema_version must exist and be published
        FormSchema schema = schemaService.getPublishedSchemaOrThrow(req.schemaId(), req.schemaVersion());

        JsonNode fieldsNode;
        try {
            fieldsNode = objectMapper.readTree(schema.getDefinition());
        } catch (Exception ex) {
            throw FormException.malformedPayload("schema definition unreadable");
        }

        // §FR-03 — enableWhen validation, rejects with 422 on violation
        enableWhenEvaluator.validateAgainstSchema(fieldsNode, req.responses());

        // §FR-05 — FHIR mapping
        String fhirResource = fhirBindingService.buildFhirResource(fieldsNode, req.responses());

        String responsesJson;
        try {
            responsesJson = objectMapper.writeValueAsString(req.responses());
        } catch (Exception ex) {
            throw FormException.malformedPayload("responses serialization failed");
        }

        FormSubmission submission = FormSubmission.builder()
            .tenantId(tenantId)
            .schemaId(req.schemaId())
            .schemaVersion(req.schemaVersion())
            .patientId(req.patientId())
            .encounterId(req.encounterId())
            .actorId(req.actorId())
            .actorRole(req.actorRole())
            .responses(responsesJson)
            .fhirResource(fhirResource)
            .timestamp(req.timestamp())
            .idempotencyKey(req.idempotencyKey())
            .build();

        FormSubmission saved = submissionRepo.save(submission);

        // §FR-06 — synchronous PLAT-002 write; REJECT submission if it fails
        try {
            auditWriteService.writeSubmissionAudit(saved, tenantId);
        } catch (Exception ex) {
            log.error("PLAT-002 audit write failed for submission {}: {}", saved.getId(), ex.getMessage());
            throw FormException.downstreamUnavailable("PLAT-002 (Audit Service)");
        }

        eventProducer.publishSubmissionCreated(saved);
        incrementCounter(tenantId, schema.getName());
        log.info("Submission created: id={} schema={} v{} tenant={}",
            saved.getId(), schema.getName(), req.schemaVersion(), tenantId);

        return new IdempotencyResult(toResponse(saved), true);
    }

    @Transactional(readOnly = true)
    public FormSubmissionResponse getById(UUID id) {
        return submissionRepo.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> FormException.notFound(id));
    }

    @Transactional(readOnly = true)
    public Page<FormSubmissionResponse> query(SubmissionQueryRequest req) {
        return submissionRepo.query(
            req.patientId(), req.encounterId(), req.schemaId(), req.actorId(),
            req.from(), req.to(), PageRequest.of(req.page(), req.size())
        ).map(this::toResponse);
    }

    private void incrementCounter(String tenant, String schemaName) {
        String key = tenant + ":" + schemaName;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder("form_submissions_total")
                .tag("tenant", tenant).tag("schema_name", schemaName)
                .register(meterRegistry)
        ).increment();
    }

    private FormSubmissionResponse toResponse(FormSubmission s) {
        return new FormSubmissionResponse(s.getId(), s.getSchemaId(), s.getSchemaVersion(),
            s.getPatientId(), s.getEncounterId(), s.getActorId(), s.getActorRole(),
            s.getResponses(), s.getFhirResource(), s.getTimestamp(),
            s.getIdempotencyKey(), s.getCreatedAt());
    }

    public record IdempotencyResult(FormSubmissionResponse response, boolean isNew) {}
}
