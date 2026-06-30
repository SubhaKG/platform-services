package com.kghospital.workflow.kafka;

import com.kghospital.workflow.domain.event.StateChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StateChangedProducer {

    private final KafkaTemplate<String, StateChangedEvent> kafkaTemplate;

    @Value("${kafka.topics.state-changed:pista.workflow.state-changed}")
    private String topic;

    public void publish(StateChangedEvent event) {
        kafkaTemplate.send(topic, event.tenantId(), event)
            .whenComplete((r, ex) -> {
                if (ex != null)
                    log.error("StateChangedEvent publish failed: {}", ex.getMessage());
                else
                    log.debug("StateChangedEvent: entity={} {}→{}",
                        event.entityId(), event.fromState(), event.toState());
            });
    }
}
