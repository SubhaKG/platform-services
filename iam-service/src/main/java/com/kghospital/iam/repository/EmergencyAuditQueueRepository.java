package com.kghospital.iam.repository;

import com.kghospital.iam.domain.entity.EmergencyAuditQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EmergencyAuditQueueRepository extends JpaRepository<EmergencyAuditQueue, UUID> {

    @Query("SELECT e FROM EmergencyAuditQueue e WHERE e.replayedAt IS NULL ORDER BY e.queuedAt ASC")
    List<EmergencyAuditQueue> findPending();

    @Query("SELECT COUNT(e) FROM EmergencyAuditQueue e WHERE e.replayedAt IS NULL AND e.retryCount > 10")
    long countHighRetry();
}
