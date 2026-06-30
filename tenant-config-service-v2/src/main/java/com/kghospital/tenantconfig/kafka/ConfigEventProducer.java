package com.kghospital.tenantconfig.kafka;

import com.kghospital.tenantconfig.domain.event.ConfigChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigEventProducer {

    private final KafkaTemplate<String, ConfigChangedEvent> kafkaTemplate;

    @Value("${kafka.topics.config-changed:pista.config.changed}")
    private String topic;

    public void publish(String tenantId, String key,
                        com.kghospital.tenantconfig.domain.enums.ResolutionScope scope,
                        String oldValue, String newValue, UUID actorId) {
        ConfigChangedEvent event = new ConfigChangedEvent(
            UUID.randomUUID(), tenantId, key, scope, oldValue, newValue, actorId, Instant.now());

        kafkaTemplate.send(topic, tenantId, event)
            .whenComplete((r, ex) -> {
                if (ex != null)
                    log.error("Failed to publish ConfigChangedEvent key={}: {}", key, ex.getMessage());
                else
                    log.debug("ConfigChangedEvent published: tenant={} key={} scope={}", tenantId, key, scope);
            });
    }
}
