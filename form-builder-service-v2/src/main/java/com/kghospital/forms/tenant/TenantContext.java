package com.kghospital.forms.tenant;
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    public static void set(String t) { CURRENT.set(t); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
