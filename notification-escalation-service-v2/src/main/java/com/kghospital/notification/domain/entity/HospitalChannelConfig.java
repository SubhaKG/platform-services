package com.kghospital.notification.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-003 §3.6.4 — per-hospital channel_config. One row per tenant.
 * FR-13: enable/disable channels + alert-to-channel map, applied without restart.
 */
@Entity
@Table(name = "hospital_channel_config")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class HospitalChannelConfig {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 64)
    private String tenantId;

    /**
     * Full channel + alert_channel_map JSON per §3.6.4:
     * { "channels": {...}, "alert_channel_map": {...} }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private String configJson;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
