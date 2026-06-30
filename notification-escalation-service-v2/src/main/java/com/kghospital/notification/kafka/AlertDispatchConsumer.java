package com.kghospital.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.notification.dto.AlertDispatchRequest;
import com.kghospital.notification.iam.IamIntrospectionClient;
import com.kghospital.notification.service.AlertDispatchService;
import com.kghospital.notification.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * PLAT-003 §3.5.1 Context A — inbound dispatch event consumer.
 *
 * Trust model decision: Option 1 — JWT on Kafka message header.
 * "The producing service embeds its service account JWT in the Kafka
 * message header. PLAT-003 validates the JWT on consume. More rigorous;
 * revocation works." This was chosen over Kafka-ACL-only because it gives
 * symmetric trust handling with the REST fallback endpoint (both paths
 * go through the same IAM introspection + ROLE_NOTIFICATION_DISPATCHER
 * check) and because revocation must work immediately platform-wide —
 * an ACL-only model can't revoke a compromised producer's trust without
 * a broker config change, whereas IAM token revocation is instant.
 *
 * §FR-01 — Kafka consumer processes event within 2s; alert instance created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDispatchConsumer {

    private final AlertDispatchService dispatchService;
    private final IamIntrospectionClient iamClient;
    private final ObjectMapper objectMapper;

    private static final String AUTH_HEADER = "Authorization";
    private static final String REQUIRED_ROLE = "ROLE_NOTIFICATION_DISPATCHER";

    @KafkaListener(
        topics = "#{notificationKafkaTopics.dispatchTopicPattern}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, AlertDispatchRequest> record, Acknowledgment ack) {
        String tenantId = record.key();
        MDC.put("tenantId", tenantId);

        try {
            // §3.5.1 Context A — validate service JWT on Kafka header before processing
            String token = extractToken(record);
            if (token == null) {
                log.error("Dispatch event missing Authorization header — rejecting: tenant={}", tenantId);
                ack.acknowledge(); // don't retry malformed producer events forever
                return;
            }

            IamIntrospectionClient.IntrospectionResult result = iamClient.introspect(token);
            if (!result.active() || !result.roles().contains(REQUIRED_ROLE.replace("ROLE_", ""))) {
                log.error("Dispatch event JWT lacks {}: tenant={}", REQUIRED_ROLE, tenantId);
                ack.acknowledge();
                return;
            }

            TenantContext.set(tenantId);
            dispatchService.dispatch(record.value());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Dispatch consumer error: tenant={} error={}", tenantId, ex.getMessage(), ex);
            throw ex; // let Spring Kafka retry / dead-letter per consumer config
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    private String extractToken(ConsumerRecord<String, AlertDispatchRequest> record) {
        Header header = record.headers().lastHeader(AUTH_HEADER);
        if (header == null) return null;
        String raw = new String(header.value(), StandardCharsets.UTF_8);
        return raw.startsWith("Bearer ") ? raw.substring(7) : raw;
    }
}
