package com.kghospital.tenantconfig.dto;

import java.util.Map;

/** §7.3 — { "resolved": { "key": { "value":..., "resolved_from":... } } } */
public record BatchResolveResponse(Map<String, ResolvedEntry> resolved) {
    public record ResolvedEntry(Object value, String resolvedFrom) {}
}
