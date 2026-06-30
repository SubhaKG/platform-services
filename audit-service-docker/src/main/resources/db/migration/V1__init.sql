CREATE TABLE IF NOT EXISTS audit_events (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(64)  NOT NULL
        CONSTRAINT chk_event_type CHECK (event_type IN (
            'CPOE_ORDER_CREATED','CPOE_ORDER_MODIFIED','CPOE_ORDER_CANCELLED',
            'CPOE_ORDER_VERIFIED','CPOE_ORDER_DISPENSED'
        )),
    actor_id         UUID         NOT NULL,
    actor_role       VARCHAR(64)  NOT NULL,
    patient_id       UUID         NOT NULL,
    encounter_id     UUID,
    resource_type    VARCHAR(64)  NOT NULL,
    resource_id      UUID         NOT NULL,
    action           VARCHAR(64)  NOT NULL,
    timestamp        TIMESTAMPTZ  NOT NULL,
    idempotency_key  VARCHAR(128),
    metadata         JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ae_patient_id   ON audit_events (patient_id);
CREATE INDEX idx_ae_encounter_id ON audit_events (encounter_id) WHERE encounter_id IS NOT NULL;
CREATE INDEX idx_ae_actor_id     ON audit_events (actor_id);
CREATE INDEX idx_ae_event_type   ON audit_events (event_type);
CREATE INDEX idx_ae_timestamp    ON audit_events (timestamp DESC);
CREATE INDEX idx_ae_resource     ON audit_events (resource_type, resource_id);

CREATE UNIQUE INDEX uq_idempotency_key ON audit_events (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE RULE no_update_audit AS ON UPDATE TO audit_events DO INSTEAD NOTHING;
CREATE RULE no_delete_audit AS ON DELETE TO audit_events DO INSTEAD NOTHING;

COMMENT ON TABLE audit_events IS 'PLAT-002 append-only audit trail. 7-year retention.';
