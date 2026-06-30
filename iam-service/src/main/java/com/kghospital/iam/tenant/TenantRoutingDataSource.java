package com.kghospital.iam.tenant;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * PLAT-001 §4.3 — Dynamic DB connection routing.
 * Returns datasource mapped to current thread's tenant ID.
 * Throws IllegalStateException → 400 if tenant context is null.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException(
                "PLAT-001-E001: Tenant context not resolved — halting DB access.");
        }
        return tenantId;
    }
}
