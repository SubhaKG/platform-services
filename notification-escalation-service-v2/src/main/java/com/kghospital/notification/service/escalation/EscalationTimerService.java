package com.kghospital.notification.service.escalation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.notification.domain.entity.AlertInstance;
import com.kghospital.notification.domain.enums.AlertStatus;
import com.kghospital.notification.domain.enums.Channel;
import com.kghospital.notification.domain.enums.NoResponseAction;
import com.kghospital.notification.kafka.NotificationEventProducer;
import com.kghospital.notification.repository.AlertInstanceRepository;
import com.kghospital.notification.service.channel.ChannelDeliveryService;
import com.kghospital.notification.service.prm.PrmClient;
import com.kghospital.notification.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-003 §3.3.2 — Escalation engine.
 *
 * FR-06 — advance to next step if no ACK within wait_hours.
 * FR-07 — execute on_no_response action when final step expires.
 * FR-08 — cancel all pending timers immediately on ACK.
 *
 * §3.4.3 — Distributed lock required across replicas. Decision: DB advisory
 * lock (pg_try_advisory_lock), scoped per-tenant connection. This fits the
 * platform's existing per-tenant datasource pattern (no Redis needed) —
 * the lock is naturally tenant-isolated since each tenant has its own
 * connection pool, so there's no cross-tenant lock contention and no new
 * infrastructure dependency, unlike a shared Redis instance would introduce.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationTimerService {

    private static final long ADVISORY_LOCK_KEY = 778899001L; // arbitrary fixed key for this job

    private final AlertInstanceRepository alertRepo;
    private final ChannelDeliveryService channelDeliveryService;
    private final PrmClient prmClient;
    private final NotificationEventProducer eventProducer;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * §FR-06 — runs every 60s. Advisory lock ensures only one replica
     * processes due escalations for this tenant connection at a time.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processDueEscalations() {
        // pg_try_advisory_lock is non-blocking — returns false if another
        // replica already holds it on this tenant's connection
        boolean acquired = (Boolean) entityManager
            .createNativeQuery("SELECT pg_try_advisory_lock(:key)")
            .setParameter("key", ADVISORY_LOCK_KEY)
            .getSingleResult();

        if (!acquired) {
            log.debug("Escalation lock held by another replica — skipping this cycle");
            return;
        }

        try {
            List<AlertInstance> due = alertRepo.findDueForEscalation(Instant.now());
            if (due.isEmpty()) return;

            log.info("Processing {} due escalations", due.size());
            for (AlertInstance alert : due) {
                try {
                    TenantContext.set(alert.getTenantId());
                    advanceEscalation(alert);
                } finally {
                    TenantContext.clear();
                }
            }
        } finally {
            entityManager.createNativeQuery("SELECT pg_advisory_unlock(:key)")
                .setParameter("key", ADVISORY_LOCK_KEY)
                .getSingleResult();
        }
    }

    private void advanceEscalation(AlertInstance alert) {
        try {
            JsonNode chain = objectMapper.readTree(alert.getEscalationChainSnapshot());
            JsonNode steps = chain.path("steps");
            int nextStepNum = alert.getCurrentStep() + 1;

            JsonNode nextStep = findStep(steps, nextStepNum);

            if (nextStep == null) {
                // FR-07 — final step expired with no ACK
                executeNoResponseAction(alert, findStep(steps, alert.getCurrentStep()));
                return;
            }

            // Advance to next step
            String recipientRule = nextStep.path("recipient_rule").asText();
            List<Channel> channels = parseChannels(nextStep.path("channels"));
            int waitHours = nextStep.path("wait_hours").asInt(4);

            var recipientIds = prmClient.resolveRecipients(
                recipientRule, alert.getTenantId(), Map.of());

            alert.setCurrentStep(nextStepNum);
            alert.setResolvedRecipientIds(recipientIds);
            alert.setStatus(AlertStatus.ESCALATED);
            alert.setNextEscalationAt(Instant.now().plusSeconds(waitHours * 3600L));
            alertRepo.save(alert);

            channelDeliveryService.deliverToConfiguredChannels(alert, alert.getTenantId(), recipientIds);

            eventProducer.publishEscalated(alert);
            incrementCounter("escalation_steps_fired_total", alert.getTenantId(), alert.getAlertType().name());

            log.info("Escalation advanced: alert={} step={} recipients={}",
                alert.getId(), nextStepNum, recipientIds.size());

        } catch (Exception ex) {
            log.error("Escalation advance failed for alert={}: {}", alert.getId(), ex.getMessage(), ex);
        }
    }

    /** FR-07 — execute on_no_response action for final step */
    private void executeNoResponseAction(AlertInstance alert, JsonNode finalStep) {
        String action = finalStep != null
            ? finalStep.path("on_no_response").asText("flag_for_manual_review")
            : "flag_for_manual_review";

        try {
            NoResponseAction noResponseAction = NoResponseAction.valueOf(action);
            switch (noResponseAction) {
                case flag_for_manual_review -> {
                    alert.setStatus(AlertStatus.MANUAL_REVIEW);
                    alert.setNextEscalationAt(null);
                    log.warn("Alert flagged for manual review: id={}", alert.getId());
                }
                case notify_HOD -> {
                    var hodIds = prmClient.resolveRecipients("HOD", alert.getTenantId(), Map.of());
                    channelDeliveryService.deliverToConfiguredChannels(alert, alert.getTenantId(), hodIds);
                    alert.setStatus(AlertStatus.MANUAL_REVIEW);
                    alert.setNextEscalationAt(null);
                    log.warn("HOD notified, alert flagged: id={}", alert.getId());
                }
            }
        } catch (IllegalArgumentException ex) {
            log.error("Unknown on_no_response action '{}' — defaulting to manual review", action);
            alert.setStatus(AlertStatus.MANUAL_REVIEW);
            alert.setNextEscalationAt(null);
        }

        alertRepo.save(alert);
    }

    /** FR-08 — cancel all pending timers immediately on ACK */
    @Transactional
    public void cancelTimers(AlertInstance alert) {
        alert.setNextEscalationAt(null);
        alertRepo.save(alert);
        log.info("Escalation timers cancelled: alert={}", alert.getId());
    }

    private JsonNode findStep(JsonNode steps, int stepNum) {
        if (!steps.isArray()) return null;
        for (JsonNode step : steps) {
            if (step.path("step").asInt() == stepNum) return step;
        }
        return null;
    }

    private List<Channel> parseChannels(JsonNode channelsNode) {
        List<Channel> result = new java.util.ArrayList<>();
        if (channelsNode.isArray()) {
            for (JsonNode ch : channelsNode) {
                try {
                    result.add(Channel.valueOf(ch.asText()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    private void incrementCounter(String name, String tenant, String alertType) {
        String key = name + ":" + tenant + ":" + alertType;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder(name).tag("tenant", tenant).tag("alert_type", alertType)
                .register(meterRegistry)
        ).increment();
    }
}
