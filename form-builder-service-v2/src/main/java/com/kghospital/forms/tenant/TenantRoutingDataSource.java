package com.kghospital.forms.tenant;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    @Override protected Object determineCurrentLookupKey() {
        String t = TenantContext.get();
        if (t == null) throw new IllegalStateException("PLAT-004-E001: Tenant context not resolved");
        return t;
    }
}
