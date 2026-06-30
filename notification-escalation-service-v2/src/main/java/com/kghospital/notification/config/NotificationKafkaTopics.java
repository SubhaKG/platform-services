package com.kghospital.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** §3.4.2 — {tenant}.platform.notification.{entity}.{event} pattern */
@Component("notificationKafkaTopics")
public class NotificationKafkaTopics {

    @Value("${kafka.topics.dispatch-pattern:+.platform.notification.alert.dispatch}")
    private String dispatchPattern;

    /** Spring Kafka topic patterns support regex; '+' matches any tenant prefix segment */
    public String getDispatchTopicPattern() {
        return dispatchPattern.replace("+", ".*");
    }
}
