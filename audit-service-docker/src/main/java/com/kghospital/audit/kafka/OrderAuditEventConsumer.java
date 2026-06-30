package com.kghospital.audit.kafka;

import com.kghospital.audit.domain.event.OrderAuditEvent;
import com.kghospital.audit.service.AuditService;
import com.kghospital.audit.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAuditEventConsumer {

    private final AuditService auditService;

    @KafkaListener(
        topics = "${kafka.topics.order-audit:cpoe.order.audit}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, OrderAuditEvent> record,
                        Acknowledgment ack) {
        String tenantId = record.key();
        MDC.put("tenantId", tenantId);
        try {
            TenantContext.set(tenantId);
            auditService.ingestFromKafka(record.value());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Audit event processing failed: tenant={} error={}",
                tenantId, ex.getMessage(), ex);
            throw ex;
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }
}
