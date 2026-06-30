package com.kghospital.notification.service.channel;

import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.domain.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * In-app channel — writes to alert_instances table only (already done by caller).
 * No external gateway call needed; in-app delivery = DB row existing.
 * PLAT-003 §1.2: in-app inbox UI itself is out of scope — front-end consumes this data.
 */
@Slf4j
@Component
public class InAppGateway implements ChannelGateway {
    @Override
    public boolean supports(Channel channel) { return channel == Channel.in_app; }

    @Override
    public String send(UUID recipientId, String contextJson, AlertType alertType) {
        log.debug("In-app delivery recorded: recipient={} alertType={}", recipientId, alertType);
        return "in_app_delivered";
    }
}
