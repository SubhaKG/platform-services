package com.kghospital.integrationregistry.tenant;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
@Component
public class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) throws Exception {
        String t = req.getHeader("X-Tenant-ID");
        if (t == null || t.isBlank()) { res.sendError(400, "Missing X-Tenant-ID"); return false; }
        TenantContext.set(t);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object h, Exception ex) {
        TenantContext.clear();
    }
}
