package com.kghospital.escalation.repository;
import com.kghospital.escalation.domain.entity.EscalationTracker;
import com.kghospital.escalation.domain.enums.EscalationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EscalationTrackerRepository extends JpaRepository<EscalationTracker, UUID> {
    @Query("""
        SELECT t FROM EscalationTracker t
        WHERE t.status = 'PENDING'
          AND t.escalateAt <= :now
        ORDER BY t.escalateAt ASC
        """)
    List<EscalationTracker> findDueEscalations(Instant now);
    Optional<EscalationTracker> findByEntityIdAndEventTypeAndStatusIn(
        UUID entityId, String eventType, List<EscalationStatus> statuses);
}
