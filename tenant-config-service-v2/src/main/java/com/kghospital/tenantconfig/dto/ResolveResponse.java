package com.kghospital.tenantconfig.dto;

import java.util.Map;

/** §7.2 — single key resolve response */
public record ResolveResponse(
    String key,
    Object resolvedValue,
    String resolvedFrom,        // role | department | hospital | platform_default
    Map<String, String> scopeContext,
    String dataType,
    boolean clinicalImpact
) {}
