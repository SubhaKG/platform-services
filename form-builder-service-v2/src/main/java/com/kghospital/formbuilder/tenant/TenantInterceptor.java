package com.kghospital.formbuilder.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sets BOTH this package's own TenantContext (used by legacy
 * FormBuilderService for its in-code tenant lookups) AND the new
 * com.kghospital.forms.tenant.TenantContext, since that is the
 * ThreadLocal actually wired to the shared TenantRoutingDataSource
 * bean (registered once in forms.config.DataSourceConfig). Without
 * this, legacy queries would hit the wrong tenant's DB connection.
 */
@Component("formBuilderTenantInterceptor")
public class TenantInterceptor implements HandlerInterceptor {
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String tenantId = req.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Tenant-ID header");
            return false;
        }
        TenantContext.set(tenantId);
        com.kghospital.forms.tenant.TenantContext.set(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        TenantContext.clear();
        com.kghospital.forms.tenant.TenantContext.clear();
    }
}
