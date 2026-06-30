package com.kghospital.forms.kafka;

import com.kghospital.forms.domain.entity.FormSubmission;
import com.kghospital.forms.domain.event.SubmissionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * §3.6.2 — {tenant}.platform.form.submission.created.
 * "Deferred to v1.2" per spec — scaffolded so the topic exists and
 * fires today; no consumers are required to exist yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormSubmissionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.submission-created-pattern:%s.platform.form.submission.created}")
    private String topicPattern;

    public void publishSubmissionCreated(FormSubmission submission) {
        String topic = String.format(topicPattern, submission.getTenantId());
        var event = new SubmissionCreatedEvent(
            submission.getId(), submission.getSchemaId(), submission.getSchemaVersion(),
            submission.getTenantId(), Instant.now());
        kafkaTemplate.send(topic, submission.getTenantId(), event);
        log.debug("Published submission.created: id={}", submission.getId());
    }
}
