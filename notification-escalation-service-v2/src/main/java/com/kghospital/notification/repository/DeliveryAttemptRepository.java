package com.kghospital.notification.repository;

import com.kghospital.notification.domain.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
}
