-- ── PLAT-003 Notification & Escalation Service ──────────────────────────────
-- Combines NEW spec (alert_instances, delivery_attempts, configs) with the
-- LEGACY generic escalation engine (escalation_rule, escalation_tracker).

-- ════════════════════════════════════════════════════════════════════════════
-- NEW: PLAT-003 Notification spec schema
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS alert_instances (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   VARCHAR(64)  NOT NULL,
    alert_type                  VARCHAR(64)  NOT NULL
        CONSTRAINT chk_alert_type CHECK (alert_type IN
            ('COSIGN_REQUEST','COSIGN_ESCALATION','COSTLY_DRUG_APPROVAL')),
    status                      VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_alert_status CHECK (status IN
            ('PENDING','SENT','ACKNOWLEDGED','ESCALATED','MANUAL_REVIEW',
             'FAILED','UNRESOLVABLE','PENDING_RESOLUTION')),
    recipient_rule              VARCHAR(64)  NOT NULL,
    resolved_recipient_ids      JSONB,
    context                     JSONB        NOT NULL,
    escalation_chain_snapshot   JSONB,
    current_step                INTEGER      NOT NULL DEFAULT 1,
    next_escalation_at          TIMESTAMPTZ,
    acknowledged_by             UUID,
    acknowledged_at             TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_status          ON alert_instances (status);
CREATE INDEX idx_ai_next_escalation ON alert_instances (next_escalation_at)
    WHERE next_escalation_at IS NOT NULL;
CREATE INDEX idx_ai_tenant          ON alert_instances (tenant_id);
CREATE INDEX idx_ai_created         ON alert_instances (created_at DESC);

CREATE TABLE IF NOT EXISTS delivery_attempts (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_instance_id  UUID         NOT NULL REFERENCES alert_instances (id) ON DELETE CASCADE,
    channel            VARCHAR(32)  NOT NULL
        CONSTRAINT chk_channel CHECK (channel IN ('in_app','sms','whatsapp','email','push')),
    recipient_id       UUID         NOT NULL,
    status             VARCHAR(32)  NOT NULL
        CONSTRAINT chk_delivery_status CHECK (status IN ('SENT','DELIVERED','FAILED')),
    attempt_number     INTEGER      NOT NULL DEFAULT 1,
    gateway_response   TEXT,
    attempted_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_da_alert     ON delivery_attempts (alert_instance_id);
CREATE INDEX idx_da_attempted ON delivery_attempts (attempted_at DESC);

CREATE TABLE IF NOT EXISTS hospital_channel_config (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(64)  NOT NULL UNIQUE,
    config_json  JSONB        NOT NULL,
    updated_at   TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS escalation_chain_config (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(64)  NOT NULL,
    alert_type  VARCHAR(64)  NOT NULL,
    chain_json  JSONB        NOT NULL,
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uq_chain_tenant_type UNIQUE (tenant_id, alert_type)
);

COMMENT ON COLUMN alert_instances.escalation_chain_snapshot IS
    'Immutable per-instance snapshot per FR-09 — config changes only affect NEW alerts.';
COMMENT ON COLUMN delivery_attempts.attempt_number IS
    '1-3 per FR-14 retry policy with exponential back-off.';

-- ════════════════════════════════════════════════════════════════════════════
-- LEGACY: generic rule-based escalation engine (kept as fallback path)
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS escalation_rule (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                VARCHAR(128) NOT NULL,
    event_type               VARCHAR(128) NOT NULL,
    initial_role             VARCHAR(64)  NOT NULL,
    sla_minutes              INTEGER      NOT NULL,
    escalate_to_role         VARCHAR(64)  NOT NULL,
    second_escalate_to_role  VARCHAR(64),
    second_sla_minutes       INTEGER,
    notification_channel     VARCHAR(32),
    is_active                BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_er_tenant_event ON escalation_rule (tenant_id, event_type) WHERE is_active = true;

CREATE TABLE IF NOT EXISTS escalation_tracker (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(128) NOT NULL,
    entity_id        UUID         NOT NULL,
    entity_type      VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(128) NOT NULL,
    rule_id          UUID         NOT NULL REFERENCES escalation_rule (id),
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    escalation_level INTEGER      NOT NULL DEFAULT 0,
    escalate_at      TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    escalated_at     TIMESTAMPTZ,
    acknowledged_at  TIMESTAMPTZ,
    acknowledged_by  VARCHAR(255),
    correlation_id   UUID
);
CREATE INDEX idx_et_entity ON escalation_tracker (entity_id);
CREATE INDEX idx_et_status ON escalation_tracker (status);
CREATE INDEX idx_et_due    ON escalation_tracker (escalate_at) WHERE status = 'PENDING';
CREATE INDEX idx_et_tenant ON escalation_tracker (tenant_id);
