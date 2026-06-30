package com.kghospital.escalation.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j @Component @RequiredArgsConstructor
public class EscalationEventProducer {

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Value("${kafka.topics.escalation-triggered:pista.escalation.triggered}")
    private String topic;

    public void publishEscalation(String tenantId, UUID entityId, String eventType,
                                   String targetRole, String channel, String message,
                                   UUID correlationId) {
        Map<String, Object> event = Map.of(
            "eventType",     "ESCALATION_TRIGGERED",
            "tenantId",      tenantId,
            "entityId",      entityId.toString(),
            "originalEvent", eventType,
            "targetRole",    targetRole,
            "channel",       channel != null ? channel : "IN_APP",
            "message",       message,
            "correlationId", correlationId != null ? correlationId.toString() : ""
        );
        kafkaTemplate.send(topic, tenantId, event);
        log.info("Escalation published: entity={} targetRole={}", entityId, targetRole);
    }
}
