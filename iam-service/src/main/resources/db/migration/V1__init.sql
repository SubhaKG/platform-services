-- PLAT-001 §13 IAM Service Schema

CREATE TABLE IF NOT EXISTS iam_users (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)   NOT NULL,
    keycloak_subject VARCHAR(255)  UNIQUE,
    username         VARCHAR(128)  NOT NULL,
    email            VARCHAR(256)  NOT NULL,
    password_hash    VARCHAR(256)  NOT NULL,
    roles            JSONB         NOT NULL DEFAULT '[]',
    is_active        BOOLEAN       NOT NULL DEFAULT true,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_iam_username ON iam_users (username) WHERE is_active = true;
CREATE UNIQUE INDEX uq_iam_email    ON iam_users (email)    WHERE is_active = true;
CREATE INDEX idx_iam_tenant         ON iam_users (tenant_id);

CREATE TABLE IF NOT EXISTS service_accounts (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(128)  NOT NULL UNIQUE,
    roles        JSONB         NOT NULL DEFAULT '["ROLE_SERVICE"]',
    is_active    BOOLEAN       NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- §9.2.4 Emergency audit queue — Tier 2 break-glass deferred audit
CREATE TABLE IF NOT EXISTS emergency_audit_queue (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    break_glass_event  JSONB       NOT NULL,
    queued_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    replayed_at        TIMESTAMPTZ,
    retry_count        INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX idx_eaq_pending ON emergency_audit_queue (queued_at ASC)
    WHERE replayed_at IS NULL;

COMMENT ON TABLE iam_users IS
    'PLAT-001 §13.1 — User profiles. Mirrors Keycloak; stored for local RBAC resolution.';
COMMENT ON COLUMN iam_users.password_hash IS
    'Argon2id hash. NEVER stored plain per §9.4.';
COMMENT ON TABLE emergency_audit_queue IS
    'PLAT-001 §9.2.4 — Tier 2 break-glass deferred audit queue. Replayed to PLAT-002 every 30s.';
COMMENT ON COLUMN emergency_audit_queue.retry_count IS
    'P0 alert fires if retry_count > 10 and event still pending per §9.2.4.';
