package com.kghospital.audit.repository;

import com.kghospital.audit.domain.entity.AuditEntry;
import com.kghospital.audit.domain.enums.CpoeEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    @Query("""
        SELECT a FROM AuditEntry a
        WHERE a.idempotencyKey = :key
          AND a.createdAt >= :cutoff
        """)
    Optional<AuditEntry> findByIdempotencyKeyWithin24h(
        @Param("key") String key,
        @Param("cutoff") Instant cutoff
    );

    @Query("""
        SELECT a FROM AuditEntry a
        WHERE (:patientId   IS NULL OR a.patientId   = :patientId)
          AND (:encounterId IS NULL OR a.encounterId = :encounterId)
          AND (:eventType   IS NULL OR a.eventType   = :eventType)
          AND (:actorId     IS NULL OR a.actorId     = :actorId)
          AND (:from        IS NULL OR a.timestamp   >= :from)
          AND (:to          IS NULL OR a.timestamp   <= :to)
        ORDER BY a.timestamp DESC
        """)
    Page<AuditEntry> query(
        @Param("patientId")   UUID patientId,
        @Param("encounterId") UUID encounterId,
        @Param("eventType")   CpoeEventType eventType,
        @Param("actorId")     UUID actorId,
        @Param("from")        Instant from,
        @Param("to")          Instant to,
        Pageable pageable
    );
}
