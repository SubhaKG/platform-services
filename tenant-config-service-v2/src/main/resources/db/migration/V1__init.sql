-- ── PLAT-005 Tenant Config Store — TENANT DB schema ─────────────────────────
-- Runs per-tenant via TenantFlywayMigrationRunner. The config_key_catalogue
-- table (shared platform schema) is a SEPARATE migration tree — see
-- db/migration/platform/ — run once against the single platform DB, not
-- per tenant.

CREATE TABLE IF NOT EXISTS config_values (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)  NOT NULL,
    config_key       VARCHAR(128) NOT NULL,
    scope            VARCHAR(16)  NOT NULL
        CONSTRAINT chk_scope CHECK (scope IN ('role','department','hospital')),
    scope_context    JSONB,
    value            JSONB        NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_status CHECK (status IN ('ACTIVE','PENDING_CLINICAL_APPROVAL','REJECTED')),
    set_by           UUID         NOT NULL,
    approved_by      UUID,
    activated_at     TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_cv_key    ON config_values (config_key);
CREATE INDEX idx_cv_scope  ON config_values (scope);
CREATE INDEX idx_cv_status ON config_values (status);
CREATE INDEX idx_cv_tenant ON config_values (tenant_id);

-- §8.2 — "unique per (hospital, key, scope, scope_context)"
-- Postgres treats NULL as distinct in unique indexes by default, which is
-- exactly right here: hospital-scope rows (scope_context IS NULL) are
-- naturally unique per (tenant_id, config_key) since there's only ever
-- one hospital-level value; department/role rows are unique per their
-- distinct scope_context JSON.
CREATE UNIQUE INDEX uq_cv_tenant_key_scope_context
    ON config_values (tenant_id, config_key, scope, scope_context);

CREATE TABLE IF NOT EXISTS config_audit_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     VARCHAR(64)  NOT NULL,
    config_key    VARCHAR(128) NOT NULL,
    module        VARCHAR(64)  NOT NULL DEFAULT '',
    scope         VARCHAR(16)  NOT NULL,
    scope_context JSONB,
    old_value     JSONB,
    new_value     JSONB,
    action        VARCHAR(16)  NOT NULL
        CONSTRAINT chk_action CHECK (action IN ('create','update','approve','reject','delete')),
    actor_id      UUID         NOT NULL,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_cal_tenant  ON config_audit_log (tenant_id);
CREATE INDEX idx_cal_key     ON config_audit_log (config_key);
CREATE INDEX idx_cal_changed ON config_audit_log (changed_at DESC);

-- §5.3 FR-11 — "Config audit records are immutable."
CREATE RULE no_update_config_audit AS ON UPDATE TO config_audit_log DO INSTEAD NOTHING;
CREATE RULE no_delete_config_audit AS ON DELETE TO config_audit_log DO INSTEAD NOTHING;

COMMENT ON TABLE config_values IS
    'PLAT-005 §8.2 — scoped config values per hospital. Resolution: role > department > hospital > platform_default.';
COMMENT ON COLUMN config_values.scope_context IS
    'NULL for hospital scope. {"dept_id":...} for department. {"role":...} for role.';
