package com.kghospital.escalation.kafka;

import com.kghospital.escalation.domain.event.DomainEventEnvelope;
import com.kghospital.escalation.service.EscalationService;
import com.kghospital.escalation.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class DomainEventConsumer {
    private final EscalationService escalationService;

    @KafkaListener(
        topics = "${kafka.topics.domain-events:pista.domain.events,pista.workflow.state-changed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "legacyKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DomainEventEnvelope> record, Acknowledgment ack) {
        try {
            TenantContext.set(record.key());
            escalationService.handleEvent(record.value());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Escalation event processing failed: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            TenantContext.clear();
        }
    }
}
