package com.kghospital.notification.service.channel;

import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.domain.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** PLAT-003 §3.8 — SMTP email gateway. */
@Slf4j
@Component
public class EmailGateway implements ChannelGateway {

    @Value("${channels.email.smtp.host:}")
    private String smtpHost;

    @Override
    public boolean supports(Channel channel) { return channel == Channel.email; }

    @Override
    public String send(UUID recipientId, String contextJson, AlertType alertType) throws Exception {
        log.info("Email dispatch: recipient={} alertType={}", recipientId, alertType);
        // Real impl: JavaMailSender via configured SMTP
        return "email_sent";
    }
}
