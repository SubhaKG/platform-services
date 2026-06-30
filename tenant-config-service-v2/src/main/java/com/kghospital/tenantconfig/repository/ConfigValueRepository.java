package com.kghospital.tenantconfig.repository;

import com.kghospital.tenantconfig.domain.entity.ConfigValue;
import com.kghospital.tenantconfig.domain.enums.ResolutionScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConfigValueRepository extends JpaRepository<ConfigValue, java.util.UUID> {

    /** §FR-06 resolution chain — only ACTIVE values participate in resolution */
    @Query("""
        SELECT v FROM ConfigValue v
        WHERE v.tenantId = :tenantId AND v.configKey = :key AND v.scope = :scope
          AND v.status = 'ACTIVE'
          AND (
            (:scopeContext IS NULL AND v.scopeContext IS NULL)
            OR v.scopeContext = :scopeContext
          )
        """)
    Optional<ConfigValue> findActiveValue(
        @Param("tenantId") String tenantId, @Param("key") String key,
        @Param("scope") ResolutionScope scope, @Param("scopeContext") String scopeContext
    );

    Optional<ConfigValue> findByTenantIdAndConfigKeyAndScopeAndScopeContext(
        String tenantId, String key, ResolutionScope scope, String scopeContext);

    Page<ConfigValue> findByTenantId(String tenantId, Pageable pageable);
}
