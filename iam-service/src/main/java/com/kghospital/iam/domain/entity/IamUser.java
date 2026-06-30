package com.kghospital.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PLAT-001 §13.1 — iam_users table.
 * Mirrors Keycloak user but stored in tenant DB for local RBAC resolution.
 */
@Entity
@Table(name = "iam_users", indexes = {
    @Index(name = "idx_iam_username", columnList = "username"),
    @Index(name = "idx_iam_email",    columnList = "email"),
    @Index(name = "idx_iam_tenant",   columnList = "tenant_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class IamUser {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Denormalised safety field — isolation via separate DB per tenant */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "keycloak_subject", unique = true)
    private String keycloakSubject;     // sub claim from Keycloak JWT

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 256)
    private String email;

    /** Argon2id hash — never stored plain per §9.4 */
    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    /** Array of role strings per §13.1 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles", columnDefinition = "jsonb", nullable = false)
    private List<String> roles;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
