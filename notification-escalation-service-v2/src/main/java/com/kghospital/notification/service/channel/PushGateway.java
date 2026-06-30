package com.kghospital.notification.service.channel;

import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.domain.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** PLAT-003 §3.8 — FCM push gateway. Best-effort — mark FAILED after retries, no further escalation. */
@Slf4j
@Component
public class PushGateway implements ChannelGateway {

    @Value("${channels.push.fcm.server-key:}")
    private String fcmServerKey;

    @Override
    public boolean supports(Channel channel) { return channel == Channel.push; }

    @Override
    public String send(UUID recipientId, String contextJson, AlertType alertType) throws Exception {
        log.info("Push dispatch (best-effort): recipient={} alertType={}", recipientId, alertType);
        return "push_sent";
    }
}
