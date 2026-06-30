package com.kghospital.forms.repository;

import com.kghospital.forms.domain.entity.FormSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, UUID> {

    /** §FR-08 — 24h idempotency dedup */
    @Query("""
        SELECT s FROM FormSubmission s
        WHERE s.idempotencyKey = :key AND s.createdAt >= :cutoff
        """)
    Optional<FormSubmission> findByIdempotencyKeyWithin24h(
        @Param("key") String key, @Param("cutoff") Instant cutoff);

    /** §FR-07 — paginated query with all filter combinations */
    @Query("""
        SELECT s FROM FormSubmission s
        WHERE (:patientId   IS NULL OR s.patientId   = :patientId)
          AND (:encounterId IS NULL OR s.encounterId = :encounterId)
          AND (:schemaId    IS NULL OR s.schemaId    = :schemaId)
          AND (:actorId     IS NULL OR s.actorId     = :actorId)
          AND (:from        IS NULL OR s.timestamp   >= :from)
          AND (:to          IS NULL OR s.timestamp   <= :to)
        ORDER BY s.timestamp DESC
        """)
    Page<FormSubmission> query(
        @Param("patientId") UUID patientId, @Param("encounterId") UUID encounterId,
        @Param("schemaId") UUID schemaId, @Param("actorId") UUID actorId,
        @Param("from") Instant from, @Param("to") Instant to,
        Pageable pageable
    );
}
