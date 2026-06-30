package com.kghospital.escalation.config;

import com.kghospital.escalation.domain.event.DomainEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Separate container factory for the legacy generic escalation engine.
 * Named distinctly from the new notification module's
 * "kafkaListenerContainerFactory" bean (which is typed for
 * AlertDispatchRequest) to avoid a bean type collision — both engines
 * coexist in this combined service per user request.
 */
@Configuration
public class LegacyKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, DomainEventEnvelope> legacyConsumerFactory() {
        JsonDeserializer<DomainEventEnvelope> deserializer =
            new JsonDeserializer<>(DomainEventEnvelope.class, false);
        deserializer.addTrustedPackages("com.kghospital.*");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-legacy");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DomainEventEnvelope> legacyKafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, DomainEventEnvelope>();
        factory.setConsumerFactory(legacyConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2);
        return factory;
    }
}
