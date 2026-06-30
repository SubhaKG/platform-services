package com.kghospital.workflow.repository;

import com.kghospital.workflow.domain.entity.EntityWorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EntityWorkflowInstanceRepository extends JpaRepository<EntityWorkflowInstance, UUID> {

    @Query("""
        SELECT i FROM EntityWorkflowInstance i
        LEFT JOIN FETCH i.history
        WHERE i.entityId = :entityId
          AND i.entityType = :entityType
          AND i.tenantId = :tenantId
        """)
    Optional<EntityWorkflowInstance> findWithHistory(
        @Param("entityId") UUID entityId,
        @Param("entityType") String entityType,
        @Param("tenantId") String tenantId
    );
}
