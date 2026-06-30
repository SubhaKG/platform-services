-- ── PLAT-012 Integration Adapter Registry — TENANT DB schema ────────────────
-- Per-tenant. The adapter_catalogue table (shared platform schema) is a
-- SEPARATE migration tree — see db/migration/platform/.

CREATE TABLE IF NOT EXISTS adapter_entries (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(64)  NOT NULL,
    adapter_id            VARCHAR(64)  NOT NULL,
    adapter_type          VARCHAR(32)  NOT NULL
        CONSTRAINT chk_adapter_type CHECK (adapter_type IN
            ('mediware_sync','cims_http','fhir_r4_rest','hl7v2_mllp',
             'abdm_middleware','webhook_post','ms_graph_api','generic_rest')),
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_status CHECK (status IN ('ACTIVE','INACTIVE','DEGRADED','SUSPENDED')),
    endpoint              TEXT         NOT NULL,
    protocol              VARCHAR(32)  NOT NULL
        CONSTRAINT chk_protocol CHECK (protocol IN ('rest','hl7v2_mllp','fhir_r4','https','smtp')),
    sync_frequency        VARCHAR(32),
    timeout_ms            INTEGER      NOT NULL DEFAULT 5000,
    retry_policy          JSONB        NOT NULL,
    -- §6.2: reference key into the secrets manager ONLY. NEVER a credential
    -- value. This column intentionally has no encryption columns alongside
    -- it — there is nothing here to encrypt.
    credential_ref        VARCHAR(256),
    config                JSONB,
    last_ping             TIMESTAMPTZ,
    last_success          TIMESTAMPTZ,
    consecutive_failures  INTEGER      NOT NULL DEFAULT 0,
    degraded_since        TIMESTAMPTZ,
    last_error            TEXT,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ae_tenant_adapter UNIQUE (tenant_id, adapter_id)
);

CREATE INDEX idx_ae_tenant     ON adapter_entries (tenant_id);
CREATE INDEX idx_ae_adapter_id ON adapter_entries (adapter_id);
CREATE INDEX idx_ae_status     ON adapter_entries (status);

COMMENT ON TABLE adapter_entries IS
    'PLAT-012 §9.2 — per-hospital adapter config. credential_ref is a secrets manager reference ONLY — see §6.2 SECURITY REQUIREMENT.';
COMMENT ON COLUMN adapter_entries.credential_ref IS
    'Opaque reference key, e.g. "apollo_chennai/formulary/api_key". The actual secret lives in the secrets manager, NEVER here.';
COMMENT ON COLUMN adapter_entries.consecutive_failures IS
    'Drives ACTIVE→DEGRADED (3 failures) and DEGRADED→SUSPENDED (10 failures) per FR-12. Resets to 0 on any success.';
