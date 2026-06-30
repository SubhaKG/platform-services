-- ── PLAT-012 §4.1 — v1.0 Canonical Adapter IDs ───────────────────────────────

INSERT INTO adapter_catalogue (adapter_id, category, direction, description, supported_types) VALUES
('formulary', 'clinical', 'inbound',
    'External formulary / drug master — syncs approved drug list, dosing guidelines, and substitution rules.',
    '["mediware_sync","generic_rest"]'),

('cims', 'clinical', 'outbound',
    'CIMS drug information service — real-time drug interaction checks, allergy alerts, contraindication lookups.',
    '["cims_http"]'),

('abdm_gateway', 'regulatory', 'both',
    'ABDM / NDHM gateway — ABHA ID linking, health record sharing consent flows, PHR integration.',
    '["fhir_r4_rest","abdm_middleware"]'),

('pacs_worklist', 'diagnostics', 'both',
    'PACS server — HL7 V2 ORM/ORU messages for radiology worklist push and result pull.',
    '["hl7v2_mllp"]'),

('external_lab', 'diagnostics', 'both',
    'External reference laboratory — order transmission and result ingest.',
    '["hl7v2_mllp","fhir_r4_rest"]'),

('billing_webhook', 'revenue', 'outbound',
    'Billing event endpoint — notifies an external billing or TPA system on billable events.',
    '["webhook_post"]'),

('discharge_webhook', 'revenue', 'outbound',
    'Discharge event endpoint — fires a structured discharge payload to downstream systems.',
    '["webhook_post"]'),

('hl7_adt_feed', 'operations', 'outbound',
    'HL7 V2 ADT feed to downstream systems requiring real-time patient movement notifications.',
    '["hl7v2_mllp"]'),

('ms365_calendar', 'operations', 'both',
    'Microsoft 365 / KG AD calendar integration — OPD appointment sync and staff scheduling.',
    '["ms_graph_api"]');
