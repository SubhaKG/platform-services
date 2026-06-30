package com.kghospital.notification.repository;

import com.kghospital.notification.domain.entity.HospitalChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface HospitalChannelConfigRepository extends JpaRepository<HospitalChannelConfig, UUID> {
    Optional<HospitalChannelConfig> findByTenantId(String tenantId);
}
