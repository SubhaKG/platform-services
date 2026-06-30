package com.kghospital.audit.service;

import com.kghospital.audit.domain.entity.AuditEntry;
import com.kghospital.audit.domain.enums.AuditAction;
import com.kghospital.audit.domain.enums.CpoeEventType;
import com.kghospital.audit.domain.event.OrderAuditEvent;
import com.kghospital.audit.dto.*;
import com.kghospital.audit.exception.AuditServiceException;
import com.kghospital.audit.repository.AuditEntryRepository;
import com.kghospital.audit.tenant.TenantContext;
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

@Slf4j
@Service
public class AuditService {

    private final AuditEntryRepository repository;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public AuditService(AuditEntryRepository repository,
                        MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public IdempotencyResult ingest(AuditEventRequest req) {
        String tenantId = TenantContext.get();

        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            var existing = repository.findByIdempotencyKeyWithin24h(req.idempotencyKey(), cutoff);
            if (existing.isPresent()) {
                log.info("Idempotent duplicate: key={}", req.idempotencyKey());
                return new IdempotencyResult(toDetailResponse(existing.get()), false);
            }
        }

        AuditEntry entry = AuditEntry.builder()
            .tenantId(tenantId)
            .eventType(req.eventType())
            .actorId(req.actorId())
            .actorRole(req.actorRole())
            .patientId(req.patientId())
            .encounterId(req.encounterId())
            .resourceType(req.resourceType())
            .resourceId(req.resourceId())
            .action(req.action())
            .timestamp(req.timestamp())
            .idempotencyKey(req.idempotencyKey())
            .metadata(req.metadata())
            .build();

        AuditEntry saved = repository.save(entry);
        incrementCounter(tenantId, req.eventType().name());
        log.info("Audit ingested: id={} tenant={} type={}", saved.getId(), tenantId, req.eventType());
        return new IdempotencyResult(toDetailResponse(saved), true);
    }

    @Transactional
    public void ingestFromKafka(OrderAuditEvent event) {
        String tenantId = TenantContext.get();
        String idemKey = event.correlationId() != null ? event.correlationId().toString() : null;

        if (idemKey != null) {
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            if (repository.findByIdempotencyKeyWithin24h(idemKey, cutoff).isPresent()) {
                log.debug("Kafka duplicate skipped: correlationId={}", idemKey);
                return;
            }
        }

        AuditEntry entry = AuditEntry.builder()
            .tenantId(tenantId)
            .eventType(mapToEventType(event.action().name()))
            .actorId(event.actorId())
            .actorRole(event.actorRole())
            .patientId(event.patientId())
            .encounterId(event.encounterId())
            .resourceType(event.orderType() != null ? event.orderType().name() : "ORDER")
            .resourceId(event.orderId())
            .action(mapToAuditAction(event.action().name()))
            .timestamp(event.occurredAt() != null ? event.occurredAt() : Instant.now())
            .idempotencyKey(idemKey)
            .build();

        repository.save(entry);
        incrementCounter(tenantId, entry.getEventType().name());
    }

    @Transactional(readOnly = true)
    public AuditEventDetailResponse getById(UUID id) {
        return repository.findById(id)
            .map(this::toDetailResponse)
            .orElseThrow(() -> AuditServiceException.notFound(id));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> query(AuditQueryRequest req) {
        return repository.query(
            req.patientId(), req.encounterId(), req.eventType(),
            req.actorId(), req.from(), req.to(),
            PageRequest.of(req.page(), req.size())
        ).map(this::toResponse);
    }

    private void incrementCounter(String tenantId, String eventType) {
        String key = tenantId + ":" + eventType;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder("audit_events_ingested_total")
                .tag("tenant", tenantId)
                .tag("event_type", eventType)
                .register(meterRegistry)
        ).increment();
    }

    private CpoeEventType mapToEventType(String name) {
        return switch (name.toUpperCase()) {
            case "ORDER_CREATED",  "CREATE"   -> CpoeEventType.CPOE_ORDER_CREATED;
            case "ORDER_MODIFIED", "MODIFY"   -> CpoeEventType.CPOE_ORDER_MODIFIED;
            case "ORDER_CANCELLED","CANCEL"   -> CpoeEventType.CPOE_ORDER_CANCELLED;
            case "ORDER_VERIFIED", "VERIFY"   -> CpoeEventType.CPOE_ORDER_VERIFIED;
            case "ORDER_DISPENSED","DISPENSE" -> CpoeEventType.CPOE_ORDER_DISPENSED;
            default -> CpoeEventType.CPOE_ORDER_MODIFIED;
        };
    }

    private AuditAction mapToAuditAction(String name) {
        try {
            return AuditAction.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return switch (name.toUpperCase()) {
                case "ORDER_CREATED"   -> AuditAction.CREATE;
                case "ORDER_MODIFIED"  -> AuditAction.MODIFY;
                case "ORDER_CANCELLED" -> AuditAction.CANCEL;
                case "ORDER_VERIFIED"  -> AuditAction.VERIFY;
                case "ORDER_DISPENSED" -> AuditAction.DISPENSE;
                default -> AuditAction.MODIFY;
            };
        }
    }

    private AuditEventResponse toResponse(AuditEntry e) {
        return new AuditEventResponse(e.getId(), e.getEventType(), e.getActorId(),
            e.getActorRole(), e.getPatientId(), e.getEncounterId(), e.getResourceType(),
            e.getResourceId(), e.getAction(), e.getTimestamp(), e.getIdempotencyKey(),
            e.getCreatedAt());
    }

    private AuditEventDetailResponse toDetailResponse(AuditEntry e) {
        return new AuditEventDetailResponse(e.getId(), e.getEventType(), e.getActorId(),
            e.getActorRole(), e.getPatientId(), e.getEncounterId(), e.getResourceType(),
            e.getResourceId(), e.getAction(), e.getTimestamp(), e.getIdempotencyKey(),
            e.getMetadata(), e.getCreatedAt());
    }

    public record IdempotencyResult(AuditEventDetailResponse response, boolean isNew) {}
}
