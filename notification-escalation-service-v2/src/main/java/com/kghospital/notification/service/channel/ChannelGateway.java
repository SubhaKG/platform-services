package com.kghospital.notification.service.channel;

import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.domain.enums.Channel;

import java.util.UUID;

/** PLAT-003 §3.8 — one implementation per gateway (Twilio, SMTP, FCM). */
public interface ChannelGateway {
    boolean supports(Channel channel);
    /** Returns raw gateway response. Throws on failure — caller handles retry. */
    String send(UUID recipientId, String contextJson, AlertType alertType) throws Exception;
}
