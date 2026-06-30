package com.kghospital.workflow.repository;

import com.kghospital.workflow.domain.entity.WorkflowDefinition;
import com.kghospital.workflow.domain.enums.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    @Query("""
        SELECT w FROM WorkflowDefinition w
        LEFT JOIN FETCH w.states
        LEFT JOIN FETCH w.transitions
        WHERE w.entityType = :entityType
          AND w.status = 'ACTIVE'
          AND (w.tenantId = :tenantId OR w.tenantId IS NULL)
        ORDER BY w.tenantId DESC NULLS LAST
        """)
    Optional<WorkflowDefinition> findEffective(
        @Param("entityType") String entityType,
        @Param("tenantId") String tenantId
    );

    Optional<WorkflowDefinition> findByEntityTypeAndTenantIdIsNullAndStatus(
        String entityType, WorkflowStatus status);
}
