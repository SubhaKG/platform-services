-- ── Form Builder Schema ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS form_definition (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    category          VARCHAR(64)  NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    version           INTEGER      NOT NULL DEFAULT 1,
    tenant_id         VARCHAR(128) NOT NULL,
    conditional_rules JSONB,
    metadata          JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    published_at      TIMESTAMPTZ,
    created_by        UUID
);

CREATE INDEX idx_form_category  ON form_definition (category);
CREATE INDEX idx_form_status    ON form_definition (status);
CREATE INDEX idx_form_tenant    ON form_definition (tenant_id);
CREATE INDEX idx_form_tenant_cat ON form_definition (tenant_id, category, status);

CREATE TABLE IF NOT EXISTS field_definition (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id          UUID        NOT NULL REFERENCES form_definition (id) ON DELETE CASCADE,
    field_key        VARCHAR(128) NOT NULL,
    label            VARCHAR(255) NOT NULL,
    type             VARCHAR(64)  NOT NULL,
    position         INTEGER      NOT NULL,
    required         BOOLEAN      NOT NULL DEFAULT false,
    validation       JSONB,
    default_value    JSONB,
    options          JSONB,
    lookup_config    JSONB,
    placeholder      VARCHAR(255),
    help_text        TEXT,
    section          VARCHAR(128),
    visible_condition TEXT,
    CONSTRAINT uq_form_field_key UNIQUE (form_id, field_key)
);

CREATE INDEX idx_field_form_id  ON field_definition (form_id);
CREATE INDEX idx_field_position ON field_definition (form_id, position);
