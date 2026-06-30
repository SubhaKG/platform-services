package com.kghospital.notification.service;

import com.kghospital.notification.domain.entity.AlertInstance;
import com.kghospital.notification.domain.enums.AlertStatus;
import com.kghospital.notification.exception.NotificationException;
import com.kghospital.notification.kafka.NotificationEventProducer;
import com.kghospital.notification.repository.AlertInstanceRepository;
import com.kghospital.notification.service.escalation.EscalationTimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-003 §3.3.3 — Acknowledgement.
 *
 * FR-10 — any recipient may ACK; cancels escalation timer.
 * FR-11 — publishes alert_acknowledged event.
 * §3.5.1 Context B — caller MUST be a listed recipient, else 403 (not 404,
 * to avoid leaking alert existence to non-recipients).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcknowledgeService {

    private final AlertInstanceRepository alertRepo;
    private final EscalationTimerService escalationTimerService;
    private final NotificationEventProducer eventProducer;

    @Transactional
    public AlertInstance acknowledge(UUID alertId, UUID actorId, String comment) {
        AlertInstance alert = alertRepo.findById(alertId)
            .orElseThrow(() -> NotificationException.notFound(alertId));

        // §3.5.1 Context B — recipient authorisation check (PLAT-003's own business logic)
        if (alert.getResolvedRecipientIds() == null ||
            !alert.getResolvedRecipientIds().contains(actorId)) {
            throw NotificationException.notRecipient();
        }

        if (alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
            throw NotificationException.alreadyAcknowledged(alertId);
        }

        // FR-08 — cancel all pending timers immediately
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(actorId);
        alert.setAcknowledgedAt(Instant.now());
        alert.setNextEscalationAt(null);
        alertRepo.save(alert);

        escalationTimerService.cancelTimers(alert);

        // FR-11 — publish alert_acknowledged so CPOE can react
        eventProducer.publishAcknowledged(alert, actorId);

        log.info("Alert acknowledged: id={} actor={}", alertId, actorId);
        return alert;
    }
}
