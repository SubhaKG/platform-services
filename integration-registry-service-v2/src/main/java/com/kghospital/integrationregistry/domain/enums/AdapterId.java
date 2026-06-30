package com.kghospital.integrationregistry.domain.enums;

/**
 * PLAT-012 §4.1 — canonical adapter_id. The stable identifier for an
 * integration concern. "Does not change when the vendor changes" —
 * a hospital switching formulary vendors updates adapter_type and
 * endpoint, never this.
 */
public enum AdapterId {
    formulary, cims, abdm_gateway, pacs_worklist, external_lab,
    billing_webhook, discharge_webhook, hl7_adt_feed, ms365_calendar
}
