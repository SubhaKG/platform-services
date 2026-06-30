package com.kghospital.formbuilder.kafka;

import com.kghospital.formbuilder.domain.event.FormPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FormEventProducer {

    private final KafkaTemplate<String, FormPublishedEvent> kafkaTemplate;

    @Value("${kafka.topics.form-published:cpoe.form.published}")
    private String topic;

    public void publishFormPublished(FormPublishedEvent event) {
        kafkaTemplate.send(topic, event.tenantId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish FormPublishedEvent: {}", ex.getMessage());
                } else {
                    log.info("FormPublishedEvent sent: formId={} tenant={}", event.formId(), event.tenantId());
                }
            });
    }
}
