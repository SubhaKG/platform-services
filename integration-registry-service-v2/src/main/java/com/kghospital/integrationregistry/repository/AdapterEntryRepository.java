package com.kghospital.integrationregistry.repository;

import com.kghospital.integrationregistry.domain.entity.AdapterEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AdapterEntryRepository extends JpaRepository<AdapterEntry, UUID> {

    /** §FR-08 — the primary Integration Engine lookup path */
    Optional<AdapterEntry> findByTenantIdAndAdapterId(String tenantId, String adapterId);

    Page<AdapterEntry> findByTenantId(String tenantId, Pageable pageable);
}
