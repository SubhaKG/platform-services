package com.kghospital.notification.tenant;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    @Override protected Object determineCurrentLookupKey() {
        String t = TenantContext.get();
        if (t == null) throw new IllegalStateException("PLAT-003-E001: Tenant context not resolved");
        return t;
    }
}
