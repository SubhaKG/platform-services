-- ── PLAT-004 v2.0 — Immutable Form Schema + Submission tables ───────────────
-- New layer alongside the legacy form_definition/field_definition tables
-- (V1). These tables are for legal-grade versioned schemas and FHIR-bound
-- submissions per PLAT-004_Form_Builder_Service_Spec_v2_0.docx.

CREATE TABLE IF NOT EXISTS form_schemas (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    version      INTEGER      NOT NULL DEFAULT 1,
    definition   JSONB        NOT NULL,
    is_published BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_schema_name_version_tenant UNIQUE (tenant_id, name, version)
);
CREATE INDEX idx_fs_name      ON form_schemas (name);
CREATE INDEX idx_fs_tenant    ON form_schemas (tenant_id);
CREATE INDEX idx_fs_published ON form_schemas (is_published);

-- §3.8.3 — legal versioning immutability, DB-level enforcement
-- (mirrors PLAT-002's audit_events immutability pattern)
CREATE OR REPLACE FUNCTION prevent_published_schema_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.is_published = true THEN
        RAISE EXCEPTION 'PLAT-004-E005: Cannot mutate a published schema version. Create a new version instead.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_published_schema_mutation
    BEFORE UPDATE OR DELETE ON form_schemas
    FOR EACH ROW EXECUTE FUNCTION prevent_published_schema_mutation();

CREATE TABLE IF NOT EXISTS form_submissions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64)  NOT NULL,
    schema_id       UUID         NOT NULL REFERENCES form_schemas (id),
    schema_version  INTEGER      NOT NULL,
    patient_id      UUID         NOT NULL,
    encounter_id    UUID,
    actor_id        UUID         NOT NULL,
    actor_role      VARCHAR(64)  NOT NULL,
    responses       JSONB        NOT NULL,
    fhir_resource   JSONB        NOT NULL,
    timestamp       TIMESTAMPTZ  NOT NULL,
    idempotency_key VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_submission_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_sub_patient   ON form_submissions (patient_id);
CREATE INDEX idx_sub_encounter ON form_submissions (encounter_id) WHERE encounter_id IS NOT NULL;
CREATE INDEX idx_sub_schema    ON form_submissions (schema_id);
CREATE INDEX idx_sub_actor     ON form_submissions (actor_id);
CREATE INDEX idx_sub_timestamp ON form_submissions (timestamp DESC);
CREATE INDEX idx_sub_tenant    ON form_submissions (tenant_id);

-- Submissions are also immutable once created (forensic record)
CREATE RULE no_update_form_submissions AS ON UPDATE TO form_submissions DO INSTEAD NOTHING;
CREATE RULE no_delete_form_submissions AS ON DELETE TO form_submissions DO INSTEAD NOTHING;

COMMENT ON TABLE form_schemas IS
    'PLAT-004 §3.7.1 — immutable, versioned form schema definitions. Append-only per name.';
COMMENT ON TABLE form_submissions IS
    'PLAT-004 §3.7.2 — append-only submission records. schema_version is a forensic snapshot.';
COMMENT ON COLUMN form_submissions.schema_version IS
    'Snapshot at submission time — remains accurate even if schema is later superseded, per §3.8.3.';
