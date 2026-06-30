package com.kghospital.notification.repository;

import com.kghospital.notification.domain.entity.EscalationChainConfig;
import com.kghospital.notification.domain.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EscalationChainConfigRepository extends JpaRepository<EscalationChainConfig, UUID> {
    Optional<EscalationChainConfig> findByTenantIdAndAlertType(String tenantId, AlertType alertType);
}
