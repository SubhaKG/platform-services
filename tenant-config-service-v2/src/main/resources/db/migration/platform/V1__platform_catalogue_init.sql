-- ── PLAT-005 §8.1 — config_key_catalogue (SHARED PLATFORM SCHEMA) ───────────
-- Run ONCE against the single shared platform database — NOT per tenant.
-- See PlatformCatalogueMigrationRunner.

CREATE TABLE IF NOT EXISTS config_key_catalogue (
    key                          VARCHAR(128) PRIMARY KEY,
    module                       VARCHAR(64)  NOT NULL,
    data_type                    VARCHAR(16)  NOT NULL
        CONSTRAINT chk_data_type CHECK (data_type IN
            ('BOOLEAN','INTEGER','STRING','STRING_ARRAY','INTEGER_ARRAY')),
    allowed_values               JSONB,
    allowed_range                JSONB,
    platform_default             JSONB        NOT NULL,
    supported_scopes             JSONB        NOT NULL,
    description                  TEXT,
    clinical_impact              BOOLEAN      NOT NULL DEFAULT false,
    requires_clinical_sign_off   BOOLEAN      NOT NULL DEFAULT false,
    status                       VARCHAR(16)  NOT NULL DEFAULT 'active'
        CONSTRAINT chk_status CHECK (status IN ('active','deprecated','archived')),
    replacement_key               VARCHAR(128)
);
CREATE INDEX idx_ckc_module ON config_key_catalogue (module);
CREATE INDEX idx_ckc_status ON config_key_catalogue (status);

COMMENT ON TABLE config_key_catalogue IS
    'PLAT-005 §8.1 — platform-wide key registry. Only ROLE_PLATFORM_ADMIN writes here. Shared across ALL tenants.';
