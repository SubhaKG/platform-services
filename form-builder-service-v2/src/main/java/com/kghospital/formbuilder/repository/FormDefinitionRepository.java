package com.kghospital.formbuilder.repository;

import com.kghospital.formbuilder.domain.entity.FormDefinition;
import com.kghospital.formbuilder.domain.enums.FormCategory;
import com.kghospital.formbuilder.domain.enums.FormStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, UUID> {

    List<FormDefinition> findByTenantIdAndCategory(String tenantId, FormCategory category);

    List<FormDefinition> findByTenantIdAndStatus(String tenantId, FormStatus status);

    Optional<FormDefinition> findByTenantIdAndCategoryAndStatus(
        String tenantId, FormCategory category, FormStatus status);

    // Latest published version for a category
    Optional<FormDefinition> findTopByTenantIdAndCategoryAndStatusOrderByVersionDesc(
        String tenantId, FormCategory category, FormStatus status);
}
