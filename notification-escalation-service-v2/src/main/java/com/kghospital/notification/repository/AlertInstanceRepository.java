package com.kghospital.notification.repository;

import com.kghospital.notification.domain.entity.AlertInstance;
import com.kghospital.notification.domain.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertInstanceRepository extends JpaRepository<AlertInstance, UUID> {

    /** §FR-06 — find alerts whose escalation timer has fired with no ACK */
    @Query("""
        SELECT a FROM AlertInstance a
        WHERE a.nextEscalationAt IS NOT NULL
          AND a.nextEscalationAt <= :now
          AND a.status NOT IN ('ACKNOWLEDGED', 'MANUAL_REVIEW', 'FAILED')
        ORDER BY a.nextEscalationAt ASC
        """)
    List<AlertInstance> findDueForEscalation(@Param("now") Instant now);

    @Query("""
        SELECT a FROM AlertInstance a
        WHERE (:recipientId IS NULL OR :recipientId MEMBER OF a.resolvedRecipientIds)
          AND (:alertType   IS NULL OR a.alertType = :alertType)
          AND (:status      IS NULL OR a.status    = :status)
          AND (:from        IS NULL OR a.createdAt >= :from)
          AND (:to          IS NULL OR a.createdAt <= :to)
        ORDER BY a.createdAt DESC
        """)
    Page<AlertInstance> query(
        @Param("recipientId") UUID recipientId,
        @Param("alertType")   com.kghospital.notification.domain.enums.AlertType alertType,
        @Param("status")      AlertStatus status,
        @Param("from")        Instant from,
        @Param("to")          Instant to,
        Pageable pageable
    );

    List<AlertInstance> findByStatus(AlertStatus status);
}
