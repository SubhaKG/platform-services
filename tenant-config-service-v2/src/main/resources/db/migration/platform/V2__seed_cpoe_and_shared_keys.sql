-- ── PLAT-005 §4.3 — v1.0 CPOE Config Key Catalogue ──────────────────────────
-- ⚠️ PRODUCT OWNER ACTION REQUIRED per spec §4.3 — Clinical Lead must
-- review and confirm these 12 keys, their platform defaults, and scopes
-- before any hospital configures values. Spec Open Issue #1 is BLOCKING.
-- Seeded here as the proposed v1.0 catalogue pending that sign-off.

INSERT INTO config_key_catalogue
    (key, module, data_type, allowed_values, allowed_range, platform_default,
     supported_scopes, description, clinical_impact, requires_clinical_sign_off, status)
VALUES
('cpoe.cosign.required', 'cpoe', 'BOOLEAN',
    '[true,false]', NULL, 'false',
    '["hospital","department","role"]',
    'Co-sign required before order activation', true, true, 'active'),

('cpoe.cosign.escalation_window_hours', 'cpoe', 'INTEGER',
    NULL, '{"min":1,"max":72}', '4',
    '["hospital","department"]',
    'Hours before co-sign request escalates to next step', true, true, 'active'),

('cpoe.cosign.final_escalation_action', 'cpoe', 'STRING',
    '["flag_for_manual_review","notify_hod","auto_approve"]', NULL, '"flag_for_manual_review"',
    '["hospital","department"]',
    'Action when escalation chain exhausted with no response', true, true, 'active'),

('cpoe.drug.costly_threshold_inr', 'cpoe', 'INTEGER',
    NULL, '{"min":0,"max":999999}', '1000',
    '["hospital","department"]',
    'Per-dose cost in INR above which a costly drug alert fires', true, true, 'active'),

('cpoe.drug.generic_substitution_allowed', 'cpoe', 'BOOLEAN',
    '[true,false]', NULL, 'true',
    '["hospital","department"]',
    'Whether the pharmacist may substitute generic for brand', true, true, 'active'),

('cpoe.drug.restrict_antibiotic_to_senior', 'cpoe', 'BOOLEAN',
    '[true,false]', NULL, 'false',
    '["hospital","department"]',
    'Restricts antibiotic orders to senior consultant role only', true, true, 'active'),

('cpoe.order.auto_expire_hours', 'cpoe', 'INTEGER',
    NULL, '{"min":0,"max":168}', '24',
    '["hospital","department"]',
    'Hours after which an unverified order automatically expires (0 = never)', true, true, 'active'),

('cpoe.order.require_diagnosis_before_order', 'cpoe', 'BOOLEAN',
    '[true,false]', NULL, 'false',
    '["hospital","department"]',
    'Blocks order entry until at least one diagnosis is recorded', true, true, 'active'),

('cpoe.formulary.enforce_formulary', 'cpoe', 'BOOLEAN',
    '[true,false]', NULL, 'true',
    '["hospital","department"]',
    'Whether orders must come from the approved formulary list', true, true, 'active'),

('cpoe.formulary.off_formulary_requires_justification', 'cpoe', 'BOOLEAN',
    '[true,false]', NULL, 'true',
    '["hospital"]',
    'Whether ordering off-formulary requires a documented justification', true, true, 'active'),

('cpoe.display.show_generic_name_first', 'cpoe', 'BOOLEAN',
    '[true,false]', NULL, 'true',
    '["hospital","department","role"]',
    'Whether the drug order screen shows generic name as the primary label', false, false, 'active'),

('cpoe.display.items_per_order_page', 'cpoe', 'INTEGER',
    '[10,20,50]', NULL, '20',
    '["hospital","role"]',
    'Number of order items shown per page in the order list view', false, false, 'active');

-- ── §4.4 — Shared Platform Config Keys (v1.0) ────────────────────────────────
INSERT INTO config_key_catalogue
    (key, module, data_type, allowed_values, allowed_range, platform_default,
     supported_scopes, description, clinical_impact, requires_clinical_sign_off, status)
VALUES
('platform.session.timeout_minutes', 'platform', 'INTEGER',
    NULL, '{"min":5,"max":480}', '30',
    '["hospital","role"]',
    'Idle session timeout before auto-logout', false, false, 'active'),

('platform.audit.retention_years', 'platform', 'INTEGER',
    NULL, '{"min":1,"max":10}', '7',
    '["hospital"]',
    'Audit log retention period in years', false, false, 'active'),

('platform.notification.expiry_warning_days', 'platform', 'INTEGER_ARRAY',
    NULL, NULL, '[30,7,1]',
    '["hospital"]',
    'Days before license expiry to send warnings', false, false, 'active'),

('platform.discharge.grace_period_hours', 'platform', 'INTEGER',
    NULL, '{"min":0,"max":72}', '24',
    '["hospital","department"]',
    'Hours after discharge approval before billing finalisation', false, false, 'active');
