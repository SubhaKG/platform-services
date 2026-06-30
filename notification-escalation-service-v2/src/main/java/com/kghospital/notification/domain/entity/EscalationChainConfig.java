package com.kghospital.notification.domain.entity;

import com.kghospital.notification.domain.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * PLAT-003 §3.6.5 — per-hospital, per-alert-type escalation chain config.
 * Snapshot captured into AlertInstance.escalationChainSnapshot at dispatch time.
 */
@Entity
@Table(name = "escalation_chain_config",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "alert_type"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EscalationChainConfig {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 64)
    private AlertType alertType;

    /** Full steps[] JSON per §3.6.5 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chain_json", nullable = false, columnDefinition = "jsonb")
    private String chainJson;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
