package com.kghospital.forms.tenant;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component("formsTenantInterceptor")
public class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) throws Exception {
        if (req.getRequestURI().startsWith("/actuator")) return true;
        String t = req.getHeader("X-Tenant-ID");
        if (t == null || t.isBlank()) {
            res.sendError(400, "PLAT-004-E001: Missing X-Tenant-ID header");
            return false;
        }
        TenantContext.set(t);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object h, Exception ex) {
        TenantContext.clear();
    }
}
