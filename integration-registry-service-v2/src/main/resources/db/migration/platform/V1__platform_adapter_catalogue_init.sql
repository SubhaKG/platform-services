-- ── PLAT-012 §9.1 — adapter_catalogue (SHARED PLATFORM SCHEMA) ──────────────
-- Run ONCE against the single shared platform database — NOT per tenant.

CREATE TABLE IF NOT EXISTS adapter_catalogue (
    adapter_id       VARCHAR(64)  PRIMARY KEY,
    category         VARCHAR(32)  NOT NULL
        CONSTRAINT chk_category CHECK (category IN
            ('clinical','regulatory','diagnostics','revenue','operations')),
    direction        VARCHAR(16)  NOT NULL
        CONSTRAINT chk_direction CHECK (direction IN ('inbound','outbound','both')),
    description      TEXT         NOT NULL,
    supported_types  JSONB        NOT NULL
);

COMMENT ON TABLE adapter_catalogue IS
    'PLAT-012 §9.1 — platform-wide canonical adapter_id registry. Only ROLE_PLATFORM_ADMIN writes here. Shared across ALL tenants.';
COMMENT ON COLUMN adapter_catalogue.adapter_id IS
    'Stable identifier — does not change when the vendor changes. e.g. formulary, cims, abdm_gateway.';
