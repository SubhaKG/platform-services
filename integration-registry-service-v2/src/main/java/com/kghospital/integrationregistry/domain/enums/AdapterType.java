package com.kghospital.integrationregistry.domain.enums;

/**
 * PLAT-012 §4.2 — adapter_type. The implementation class for a given
 * adapter_id + vendor. "A new vendor = a new adapter_type registered
 * by Platform Core."
 */
public enum AdapterType {
    mediware_sync, cims_http, fhir_r4_rest, hl7v2_mllp,
    abdm_middleware, webhook_post, ms_graph_api, generic_rest
}
