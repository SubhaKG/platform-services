package com.kghospital.escalation.service;

import com.kghospital.escalation.domain.entity.*;
import com.kghospital.escalation.domain.enums.EscalationStatus;
import com.kghospital.escalation.domain.event.DomainEventEnvelope;
import com.kghospital.escalation.kafka.EscalationEventProducer;
import com.kghospital.escalation.repository.*;
import com.kghospital.escalation.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Escalation Engine.
 *
 * On domain event arrival:
 *   1. Match against active escalation rules for tenant + eventType
 *   2. Create EscalationTracker with escalateAt = now + slaMinutes
 *
 * @Scheduled processor (every minute):
 *   3. Find all trackers where escalateAt <= now AND status = PENDING
 *   4. Level 0→1: notify escalateToRole, set escalateAt += secondSlaMinutes
 *   5. Level 1→2: notify secondEscalateToRole, mark ESCALATED
 *   6. Level 2: mark EXPIRED — no further escalation
 *
 * Acknowledgement:
 *   7. Any service can call acknowledge(entityId, eventType) to close the tracker
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRuleRepository ruleRepo;
    private final EscalationTrackerRepository trackerRepo;
    private final EscalationEventProducer eventProducer;

    /**
     * Called by Kafka consumer when a domain event arrives.
     * Creates tracker if a matching escalation rule exists.
     */
    @Transactional
    public void handleEvent(DomainEventEnvelope event) {
        String tenantId = TenantContext.get();
        List<EscalationRule> rules = ruleRepo
            .findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, event.eventType());

        for (EscalationRule rule : rules) {
            // Don't duplicate if already tracking this entity+event
            boolean alreadyTracking = trackerRepo
                .findByEntityIdAndEventTypeAndStatusIn(
                    event.entityId(), event.eventType(),
                    List.of(EscalationStatus.PENDING, EscalationStatus.ESCALATED))
                .isPresent();

            if (alreadyTracking) continue;

            EscalationTracker tracker = EscalationTracker.builder()
                .tenantId(tenantId)
                .entityId(event.entityId())
                .entityType(event.entityType())
                .eventType(event.eventType())
                .rule(rule)
                .escalationLevel(0)
                .escalateAt(Instant.now().plus(rule.getSlaMinutes(), ChronoUnit.MINUTES))
                .correlationId(event.correlationId())
                .build();

            trackerRepo.save(tracker);
            log.info("Escalation tracker created: entity={} eventType={} escalateAt={}",
                event.entityId(), event.eventType(), tracker.getEscalateAt());
        }
    }

    /**
     * Scheduled every minute — processes all due escalations.
     * Per PISTA spec: SLA breach → escalate to supervisor.
     */
    @Scheduled(fixedDelayString = "${escalation.processor.interval-ms:60000}")
    @Transactional
    public void processEscalations() {
        List<EscalationTracker> due = trackerRepo.findDueEscalations(Instant.now());
        if (due.isEmpty()) return;

        log.info("Processing {} due escalations", due.size());

        for (EscalationTracker tracker : due) {
            try {
                TenantContext.set(tracker.getTenantId());
                escalate(tracker);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void escalate(EscalationTracker tracker) {
        EscalationRule rule = tracker.getRule();

        switch (tracker.getEscalationLevel()) {
            case 0 -> {
                // First escalation: notify escalateToRole
                eventProducer.publishEscalation(
                    tracker.getTenantId(),
                    tracker.getEntityId(),
                    tracker.getEventType(),
                    rule.getEscalateToRole(),
                    rule.getNotificationChannel(),
                    "SLA breached — escalated from " + rule.getInitialRole(),
                    tracker.getCorrelationId()
                );
                tracker.setEscalationLevel(1);
                tracker.setEscalatedAt(Instant.now());

                if (rule.getSecondEscalateToRole() != null && rule.getSecondSlaMinutes() != null) {
                    // Schedule 2nd escalation
                    tracker.setEscalateAt(Instant.now().plus(rule.getSecondSlaMinutes(), ChronoUnit.MINUTES));
                    tracker.setStatus(EscalationStatus.ESCALATED);
                } else {
                    tracker.setStatus(EscalationStatus.ESCALATED);
                    tracker.setEscalateAt(Instant.MAX); // no further escalation
                }
            }
            case 1 -> {
                // Second escalation: notify second-level role (HOD, chief)
                if (rule.getSecondEscalateToRole() != null) {
                    eventProducer.publishEscalation(
                        tracker.getTenantId(),
                        tracker.getEntityId(),
                        tracker.getEventType(),
                        rule.getSecondEscalateToRole(),
                        rule.getNotificationChannel(),
                        "Critical SLA breach — second-level escalation",
                        tracker.getCorrelationId()
                    );
                }
                tracker.setEscalationLevel(2);
                tracker.setEscalateAt(Instant.MAX);
            }
            default -> {
                tracker.setStatus(EscalationStatus.EXPIRED);
                log.warn("Escalation expired without acknowledgement: id={}", tracker.getId());
            }
        }

        trackerRepo.save(tracker);
    }

    /**
     * Called when the responsible role acts on the entity.
     * Closes the escalation tracker.
     */
    @Transactional
    public void acknowledge(UUID entityId, String eventType, String acknowledgedBy) {
        trackerRepo.findByEntityIdAndEventTypeAndStatusIn(
            entityId, eventType,
            List.of(EscalationStatus.PENDING, EscalationStatus.ESCALATED)
        ).ifPresent(tracker -> {
            tracker.setStatus(EscalationStatus.ACKNOWLEDGED);
            tracker.setAcknowledgedAt(Instant.now());
            tracker.setAcknowledgedBy(acknowledgedBy);
            trackerRepo.save(tracker);
            log.info("Escalation acknowledged: entityId={} by={}", entityId, acknowledgedBy);
        });
    }
}
