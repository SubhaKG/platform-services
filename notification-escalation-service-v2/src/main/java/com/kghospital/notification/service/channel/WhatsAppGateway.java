package com.kghospital.notification.service.channel;

import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.domain.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** PLAT-003 §3.8 — Twilio WhatsApp BSP gateway. Same retry policy as SMS. */
@Slf4j
@Component
public class WhatsAppGateway implements ChannelGateway {

    @Value("${channels.whatsapp.twilio.bsp-token:}")
    private String bspToken;

    @Override
    public boolean supports(Channel channel) { return channel == Channel.whatsapp; }

    @Override
    public String send(UUID recipientId, String contextJson, AlertType alertType) throws Exception {
        log.info("WhatsApp dispatch: recipient={} alertType={}", recipientId, alertType);
        // Real impl: call Twilio WhatsApp BSP API
        return "whatsapp_sent";
    }
}
