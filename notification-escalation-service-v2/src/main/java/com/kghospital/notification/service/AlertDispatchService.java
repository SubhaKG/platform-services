package com.kghospital.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.notification.domain.entity.AlertInstance;
import com.kghospital.notification.domain.entity.EscalationChainConfig;
import com.kghospital.notification.domain.enums.AlertStatus;
import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.dto.AlertDispatchRequest;
import com.kghospital.notification.exception.NotificationException;
import com.kghospital.notification.kafka.NotificationEventProducer;
import com.kghospital.notification.repository.AlertInstanceRepository;
import com.kghospital.notification.repository.EscalationChainConfigRepository;
import com.kghospital.notification.service.channel.ChannelDeliveryService;
import com.kghospital.notification.service.prm.PrmClient;
import com.kghospital.notification.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-003 §3.3.1 — Alert Dispatch.
 *
 * FR-01 — accept dispatch request, create alert instance.
 * FR-02 — resolve recipient via PRM; PENDING_RESOLUTION on PRM down, UNRESOLVABLE on empty result.
 * FR-03/04 — validate alert_type whitelist, look up channel map, dead-letter unknowns.
 * FR-05 — record delivery attempts.
 * FR-09 — snapshot escalation chain at dispatch time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDispatchService {

    private final AlertInstanceRepository alertRepo;
    private final EscalationChainConfigRepository chainConfigRepo;
    private final PrmClient prmClient;
    private final ChannelDeliveryService channelDeliveryService;
    private final NotificationEventProducer eventProducer;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    @Transactional
    public AlertInstance dispatch(AlertDispatchRequest req) {
        String tenantId = TenantContext.get();

        // FR-04 — v1.0 whitelist check; unknown types are dead-lettered
        AlertType alertType;
        try {
            alertType = AlertType.valueOf(req.alertType());
        } catch (IllegalArgumentException ex) {
            eventProducer.publishDeadLetter(req, "Unknown alert_type: " + req.alertType());
            throw NotificationException.alertTypeNotSupported(req.alertType());
        }

        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(req.context());
        } catch (Exception ex) {
            throw NotificationException.malformedPayload("context serialization failed");
        }

        // FR-09 — snapshot escalation chain config at dispatch time
        String chainSnapshot = chainConfigRepo
            .findByTenantIdAndAlertType(tenantId, alertType)
            .map(EscalationChainConfig::getChainJson)
            .orElse("{\"steps\":[]}");

        AlertInstance alert = AlertInstance.builder()
            .tenantId(tenantId)
            .alertType(alertType)
            .recipientRule(req.recipientRule())
            .context(contextJson)
            .escalationChainSnapshot(chainSnapshot)
            .status(AlertStatus.PENDING)
            .build();

        // FR-02 — resolve recipient via PRM
        try {
            List<UUID> recipientIds = prmClient.resolveRecipients(
                req.recipientRule(), tenantId, req.context());

            if (recipientIds.isEmpty()) {
                alert.setStatus(AlertStatus.UNRESOLVABLE);
                alertRepo.save(alert);
                log.warn("Alert UNRESOLVABLE — PRM returned no result: rule={} tenant={}",
                    req.recipientRule(), tenantId);
                // TODO: notify ops of unresolvable alert
                return alert;
            }

            alert.setResolvedRecipientIds(recipientIds);
            alert.setStatus(AlertStatus.SENT);

            // First escalation step wait window
            applyFirstStepTimer(alert, chainSnapshot);

            alertRepo.save(alert);

            channelDeliveryService.deliverToConfiguredChannels(alert, tenantId, recipientIds);

            incrementCounter("notifications_dispatched_total", tenantId, alertType.name());
            log.info("Alert dispatched: id={} type={} recipients={}",
                alert.getId(), alertType, recipientIds.size());

            return alert;

        } catch (IllegalStateException prmDown) {
            // §3.8 — PRM unavailable, queue for retry rather than drop
            alert.setStatus(AlertStatus.PENDING_RESOLUTION);
            alertRepo.save(alert);
            log.warn("PRM unavailable — alert queued PENDING_RESOLUTION: id={}", alert.getId());
            return alert;
        }
    }

    private void applyFirstStepTimer(AlertInstance alert, String chainSnapshot) {
        try {
            JsonNode chain = objectMapper.readTree(chainSnapshot);
            JsonNode steps = chain.path("steps");
            for (JsonNode step : steps) {
                if (step.path("step").asInt() == 1) {
                    int waitHours = step.path("wait_hours").asInt(4);
                    alert.setNextEscalationAt(Instant.now().plusSeconds(waitHours * 3600L));
                    return;
                }
            }
        } catch (Exception ex) {
            log.warn("Could not parse chain snapshot for first-step timer: {}", ex.getMessage());
        }
    }

    private void incrementCounter(String name, String tenant, String alertType) {
        String key = name + ":" + tenant + ":" + alertType;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder(name).tag("tenant", tenant).tag("alert_type", alertType)
                .register(meterRegistry)
        ).increment();
    }
}
