package com.kghospital.iam.tenant;

/**
 * PLAT-001 §4.2 — ThreadLocal tenant holder.
 * Cleared in finally block after every request to prevent thread pool leakage.
 */
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(String tenantId) { CURRENT_TENANT.set(tenantId); }
    public static String getTenantId()               { return CURRENT_TENANT.get(); }
    public static void clear()                       { CURRENT_TENANT.remove(); }
}
