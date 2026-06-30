package com.kghospital.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.notification.domain.entity.AlertInstance;
import com.kghospital.notification.domain.event.*;
import com.kghospital.notification.dto.AlertDispatchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-003 §3.6.2 — Kafka topics, all pattern {tenant}.platform.notification.{entity}.{event}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.acknowledged-pattern:%s.platform.notification.alert.acknowledged}")
    private String ackTopicPattern;

    @Value("${kafka.topics.escalated-pattern:%s.platform.notification.alert.escalated}")
    private String escalatedTopicPattern;

    @Value("${kafka.topics.deadletter-pattern:%s.platform.notification.alert.deadletter}")
    private String deadLetterTopicPattern;

    public void publishAcknowledged(AlertInstance alert, UUID actorId) {
        String topic = String.format(ackTopicPattern, alert.getTenantId());
        var event = new AlertAcknowledgedEvent(
            alert.getId(), alert.getAlertType().name(), alert.getTenantId(),
            actorId, Instant.now());
        kafkaTemplate.send(topic, alert.getTenantId(), event);
        log.debug("Published alert_acknowledged: alert={}", alert.getId());
    }

    public void publishEscalated(AlertInstance alert) {
        String topic = String.format(escalatedTopicPattern, alert.getTenantId());
        var event = new AlertEscalatedEvent(
            alert.getId(), alert.getAlertType().name(), alert.getTenantId(),
            alert.getCurrentStep(), Instant.now());
        kafkaTemplate.send(topic, alert.getTenantId(), event);
        log.debug("Published alert_escalated: alert={} step={}", alert.getId(), alert.getCurrentStep());
    }

    public void publishDeadLetter(AlertDispatchRequest req, String reason) {
        String topic = String.format(deadLetterTopicPattern, req.hospitalId());
        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(req);
        } catch (Exception ex) {
            rawPayload = req.toString();
        }
        var event = new DeadLetterEvent(req.alertType(), req.hospitalId(), reason, rawPayload, Instant.now());
        kafkaTemplate.send(topic, req.hospitalId(), event);
        log.warn("Dead-lettered alert: type={} hospital={} reason={}",
            req.alertType(), req.hospitalId(), reason);
    }
}
