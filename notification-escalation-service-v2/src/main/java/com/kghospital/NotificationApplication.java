package com.kghospital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PLAT-003 Notification & Escalation Service.
 *
 * Combines two engines:
 * 1. NEW (this spec, v1.0): PRM-resolved, channel-delivered alert dispatch
 *    for COSIGN_REQUEST / COSIGN_ESCALATION / COSTLY_DRUG_APPROVAL.
 * 2. LEGACY (com.kghospital.escalation package): generic rule-based SLA
 *    escalation tracker for domain events not in the v1.0 alert whitelist
 *    — kept as a fallback path for broader platform escalation needs
 *    beyond this spec's three named alert types.
 */
@SpringBootApplication(scanBasePackages = {"com.kghospital.notification", "com.kghospital.escalation"})
@EnableScheduling
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
