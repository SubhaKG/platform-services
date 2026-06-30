package com.kghospital.forms.repository;

import com.kghospital.forms.domain.entity.FormSchema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FormSchemaRepository extends JpaRepository<FormSchema, UUID> {

    Optional<FormSchema> findByTenantIdAndNameAndVersion(String tenantId, String name, Integer version);

    @Query("""
        SELECT MAX(f.version) FROM FormSchema f
        WHERE f.tenantId = :tenantId AND f.name = :name
        """)
    Optional<Integer> findMaxVersion(@Param("tenantId") String tenantId, @Param("name") String name);

    Page<FormSchema> findByTenantIdAndNameOrderByVersionDesc(
        String tenantId, String name, Pageable pageable);

    /** FR-04 — submission validation: schema must exist AND be published */
    Optional<FormSchema> findByIdAndIsPublishedTrue(UUID id);
}
