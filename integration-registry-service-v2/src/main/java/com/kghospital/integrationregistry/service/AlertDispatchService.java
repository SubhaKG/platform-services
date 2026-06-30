package com.kghospital.integrationregistry.service;

import com.kghospital.integrationregistry.domain.entity.AdapterEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * PLAT-012 §6.4 FR-13 — "On ACTIVE → DEGRADED and DEGRADED → SUSPENDED
 * transitions, PLAT-012 MUST dispatch an alert via PLAT-003 to
 * ROLE_HOSPITAL_ADMIN and ROLE_PLATFORM_ADMIN with the adapter_id,
 * hospital_id, failure count, and last error."
 *
 * Dispatches via Kafka to notification-escalation-service's inbound
 * dispatch topic — matches PLAT-003's own spec contract
 * ({tenant}.platform.notification.alert.dispatch) rather than a
 * synchronous REST call, consistent with "PLAT-003 failure does not
 * block state transitions" in §10.
 */
@Slf4j
@Service
public class AlertDispatchService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.notification-dispatch-pattern:%s.platform.notification.alert.dispatch}")
    private String dispatchPattern;

    public AlertDispatchService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void dispatchStateTransitionAlert(AdapterEntry entry, String toStatus) {
        try {
            String topic = String.format(dispatchPattern, entry.getTenantId());
            Map<String, Object> event = Map.of(
                "alertType",     "ADAPTER_" + toStatus,   // not in PLAT-003 v1.0 whitelist —
                                                            // will dead-letter there until PLAT-003
                                                            // expands its alert type catalogue (its
                                                            // own §11 Open Issue #6)
                "hospitalId",    entry.getTenantId(),
                "recipientRule", "hospital_and_platform_admin",
                "context", Map.of(
                    "adapterId", entry.getAdapterId(),
                    "hospitalId", entry.getTenantId(),
                    "consecutiveFailures", entry.getConsecutiveFailures(),
                    "lastError", entry.getLastError() != null ? entry.getLastError() : ""
                )
            );
            kafkaTemplate.send(topic, entry.getTenantId(), event);
            log.info("State transition alert dispatched: adapter={} status={}",
                entry.getAdapterId(), toStatus);
        } catch (Exception ex) {
            // §10 — PLAT-003 failure does not block the state transition
            log.warn("Alert dispatch failed (non-blocking) for adapter={}: {}",
                entry.getAdapterId(), ex.getMessage());
        }
    }
}
