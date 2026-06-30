package com.kghospital.notification.service.channel;

import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.domain.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * PLAT-003 §3.8 — Twilio SMS gateway.
 * §3.5.3 — SMS body MUST NOT include patient names/diagnoses — order ID
 * and masked patient reference only.
 */
@Slf4j
@Component
public class SmsGateway implements ChannelGateway {

    private final RestTemplate restTemplate;

    @Value("${channels.sms.twilio.account-sid:}")
    private String accountSid;

    @Value("${channels.sms.twilio.auth-token:}")
    private String authToken;

    @Value("${channels.sms.twilio.from-number:}")
    private String fromNumber;

    public SmsGateway(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(Channel channel) { return channel == Channel.sms; }

    @Override
    public String send(UUID recipientId, String contextJson, AlertType alertType) throws Exception {
        // §3.5.3 — minimal context only: order ID + masked patient ref, no names/diagnoses
        String message = buildSafeMessage(alertType, contextJson);
        log.info("SMS dispatch: recipient={} alertType={}", recipientId, alertType);
        // Real impl: call Twilio API with accountSid/authToken/fromNumber
        // Placeholder — wire to actual Twilio client in production
        return "sms_sent:" + message.hashCode();
    }

    private String buildSafeMessage(AlertType alertType, String contextJson) {
        return switch (alertType) {
            case COSIGN_REQUEST       -> "Co-sign required on a pending order. Please review.";
            case COSIGN_ESCALATION    -> "ESCALATED: Co-sign overdue on a pending order.";
            case COSTLY_DRUG_APPROVAL -> "Costly drug order requires your approval.";
        };
    }
}
