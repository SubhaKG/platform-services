package com.kghospital.integrationregistry.kafka;

import com.kghospital.integrationregistry.domain.entity.AdapterEntry;
import com.kghospital.integrationregistry.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * PLAT-012 §8.2 — 5 distinct topic patterns, all {tenant}.platform.adapter.{event}.
 * "PLAT-003 failure does not block state transitions" — Kafka publish
 * failures here are logged but never thrown back into the calling
 * health-state-machine flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdapterEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.degraded-pattern:%s.platform.adapter.degraded}")
    private String degradedPattern;
    @Value("${kafka.topics.suspended-pattern:%s.platform.adapter.suspended}")
    private String suspendedPattern;
    @Value("${kafka.topics.recovered-pattern:%s.platform.adapter.recovered}")
    private String recoveredPattern;
    @Value("${kafka.topics.credential-rotated-pattern:%s.platform.adapter.credential_rotated}")
    private String credentialRotatedPattern;
    @Value("${kafka.topics.config-updated-pattern:%s.platform.adapter.config_updated}")
    private String configUpdatedPattern;

    public void publishDegraded(AdapterEntry entry, String fromStatus) {
        publish(String.format(degradedPattern, entry.getTenantId()), entry.getTenantId(),
            new AdapterStateTransitionEvent(entry.getTenantId(), entry.getAdapterId(),
                fromStatus, "DEGRADED", entry.getConsecutiveFailures(), entry.getLastError(), Instant.now()));
    }

    public void publishSuspended(AdapterEntry entry) {
        publish(String.format(suspendedPattern, entry.getTenantId()), entry.getTenantId(),
            new AdapterStateTransitionEvent(entry.getTenantId(), entry.getAdapterId(),
                "DEGRADED", "SUSPENDED", entry.getConsecutiveFailures(), entry.getLastError(), Instant.now()));
    }

    public void publishRecovered(AdapterEntry entry) {
        publish(String.format(recoveredPattern, entry.getTenantId()), entry.getTenantId(),
            new AdapterStateTransitionEvent(entry.getTenantId(), entry.getAdapterId(),
                "SUSPENDED", "ACTIVE", 0, null, Instant.now()));
    }

    public void publishCredentialRotated(String tenantId, String adapterId, String newRef) {
        publish(String.format(credentialRotatedPattern, tenantId), tenantId,
            new CredentialRotatedEvent(tenantId, adapterId, newRef, Instant.now()));
    }

    public void publishConfigUpdated(String tenantId, String adapterId) {
        publish(String.format(configUpdatedPattern, tenantId), tenantId,
            new AdapterConfigUpdatedEvent(tenantId, adapterId, Instant.now()));
    }

    private void publish(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Kafka publish failed (non-blocking): topic={} error={}", topic, ex.getMessage());
                else log.debug("Published: topic={}", topic);
            });
    }
}
