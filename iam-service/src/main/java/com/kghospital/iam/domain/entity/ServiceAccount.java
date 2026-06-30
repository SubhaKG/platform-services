package com.kghospital.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PLAT-001 §13.2 — service_accounts table.
 * S2S service accounts with ROLE_SERVICE.
 */
@Entity
@Table(name = "service_accounts", indexes = {
    @Index(name = "idx_sa_service_name", columnList = "service_name")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ServiceAccount {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_name", nullable = false, unique = true, length = 128)
    private String serviceName;    // e.g. plat-003-workflow (= Keycloak clientId)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles", columnDefinition = "jsonb", nullable = false)
    private List<String> roles;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
