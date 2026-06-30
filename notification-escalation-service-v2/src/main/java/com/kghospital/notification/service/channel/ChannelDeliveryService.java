package com.kghospital.notification.service.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.notification.domain.entity.AlertInstance;
import com.kghospital.notification.domain.entity.DeliveryAttempt;
import com.kghospital.notification.domain.entity.HospitalChannelConfig;
import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.domain.enums.Channel;
import com.kghospital.notification.domain.enums.DeliveryStatus;
import com.kghospital.notification.repository.DeliveryAttemptRepository;
import com.kghospital.notification.repository.HospitalChannelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PLAT-003 §3.3.1 FR-03, FR-14 — channel selection + delivery with retry.
 *
 * "If a channel delivery fails, retry up to 3 times with exponential
 * back-off before marking FAILED and continuing to the next channel."
 *
 * "Channel gateway failures MUST NOT fail the entire alert dispatch —
 * deliver to available channels and retry failed channels independently."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelDeliveryService {

    private final HospitalChannelConfigRepository configRepo;
    private final DeliveryAttemptRepository deliveryRepo;
    private final List<ChannelGateway> gateways;
    private final ObjectMapper objectMapper;

    /**
     * FR-03 — look up alert_type in hospital's alert-to-channel map,
     * dispatch to each configured channel independently.
     */
    public List<DeliveryAttempt> deliverToConfiguredChannels(
            AlertInstance alert, String tenantId, List<UUID> recipientIds) {

        HospitalChannelConfig config = configRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new IllegalStateException(
                "No channel config for tenant: " + tenantId));

        List<Channel> channels = resolveChannelsForAlertType(config, alert.getAlertType());
        List<DeliveryAttempt> attempts = new ArrayList<>();

        for (Channel channel : channels) {
            ChannelGateway gateway = findGateway(channel);
            if (gateway == null) {
                log.warn("No gateway registered for channel: {}", channel);
                continue;
            }

            for (UUID recipientId : recipientIds) {
                DeliveryAttempt attempt = deliverWithRetry(gateway, alert, channel, recipientId);
                deliveryRepo.save(attempt);
                attempts.add(attempt);
            }
        }

        return attempts;
    }

    /**
     * FR-14 — retry up to 3 times with exponential back-off.
     * Channel failure does not block other channels per §3.4.4.
     */
    private DeliveryAttempt deliverWithRetry(ChannelGateway gateway, AlertInstance alert,
                                              Channel channel, UUID recipientId) {
        int maxAttempts = 3;
        String lastError = null;

        for (int attemptNum = 1; attemptNum <= maxAttempts; attemptNum++) {
            try {
                String response = gateway.send(recipientId, alert.getContext(), alert.getAlertType());
                return DeliveryAttempt.builder()
                    .alertInstance(alert)
                    .channel(channel)
                    .recipientId(recipientId)
                    .status(DeliveryStatus.SENT)
                    .attemptNumber(attemptNum)
                    .gatewayResponse(response)
                    .attemptedAt(Instant.now())
                    .build();
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log.warn("Channel delivery attempt {} failed: channel={} recipient={} error={}",
                    attemptNum, channel, recipientId, lastError);
                if (attemptNum < maxAttempts) {
                    try {
                        Thread.sleep((long) Math.pow(2, attemptNum) * 1000); // exponential back-off
                    } catch (InterruptedException ignored) {}
                }
            }
        }

        // All retries exhausted — mark FAILED, continue to next channel
        return DeliveryAttempt.builder()
            .alertInstance(alert)
            .channel(channel)
            .recipientId(recipientId)
            .status(DeliveryStatus.FAILED)
            .attemptNumber(maxAttempts)
            .gatewayResponse(lastError)
            .attemptedAt(Instant.now())
            .build();
    }

    private List<Channel> resolveChannelsForAlertType(HospitalChannelConfig config, AlertType alertType) {
        try {
            JsonNode root = objectMapper.readTree(config.getConfigJson());
            JsonNode mapped = root.path("alert_channel_map").path(alertType.name());
            List<Channel> result = new ArrayList<>();
            if (mapped.isArray()) {
                for (JsonNode ch : mapped) {
                    try {
                        result.add(Channel.valueOf(ch.asText()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            return result;
        } catch (Exception ex) {
            log.error("Failed to parse channel config: {}", ex.getMessage());
            return List.of();
        }
    }

    private ChannelGateway findGateway(Channel channel) {
        return gateways.stream()
            .filter(g -> g.supports(channel))
            .findFirst()
            .orElse(null);
    }
}
