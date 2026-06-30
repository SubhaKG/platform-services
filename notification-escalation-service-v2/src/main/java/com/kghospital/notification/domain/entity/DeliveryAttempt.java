package com.kghospital.notification.domain.entity;

import com.kghospital.notification.domain.enums.Channel;
import com.kghospital.notification.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** PLAT-003 §3.7.2 — delivery_attempts. One row per channel attempt per alert. */
@Entity
@Table(name = "delivery_attempts", indexes = {
    @Index(name = "idx_da_alert",      columnList = "alert_instance_id"),
    @Index(name = "idx_da_attempted",  columnList = "attempted_at DESC")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DeliveryAttempt {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_instance_id", nullable = false)
    private AlertInstance alertInstance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Channel channel;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status;

    /** 1-3 retry count per FR-14 */
    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(name = "attempted_at", nullable = false)
    @Builder.Default
    private Instant attemptedAt = Instant.now();
}
