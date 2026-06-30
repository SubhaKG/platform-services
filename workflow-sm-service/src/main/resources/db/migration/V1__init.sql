CREATE TABLE IF NOT EXISTS workflow_definition (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type   VARCHAR(64)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    version       INTEGER      NOT NULL DEFAULT 1,
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id     VARCHAR(128),
    initial_state VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);
CREATE INDEX idx_wf_entity_type ON workflow_definition (entity_type);
CREATE INDEX idx_wf_tenant      ON workflow_definition (tenant_id) WHERE tenant_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS state_definition (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id  UUID         NOT NULL REFERENCES workflow_definition (id) ON DELETE CASCADE,
    state_code   VARCHAR(64)  NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    is_terminal  BOOLEAN      NOT NULL DEFAULT false,
    is_enabled   BOOLEAN      NOT NULL DEFAULT true,
    description  TEXT,
    sla_minutes  INTEGER,
    CONSTRAINT uq_state_workflow UNIQUE (workflow_id, state_code)
);
CREATE INDEX idx_state_workflow ON state_definition (workflow_id);

CREATE TABLE IF NOT EXISTS transition_definition (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id   UUID         NOT NULL REFERENCES workflow_definition (id) ON DELETE CASCADE,
    from_state    VARCHAR(64)  NOT NULL,
    to_state      VARCHAR(64)  NOT NULL,
    action_code   VARCHAR(64)  NOT NULL,
    trigger       VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    allowed_roles TEXT,
    is_enabled    BOOLEAN      NOT NULL DEFAULT true,
    description   TEXT,
    CONSTRAINT uq_transition UNIQUE (workflow_id, from_state, action_code)
);
CREATE INDEX idx_transition_workflow   ON transition_definition (workflow_id);
CREATE INDEX idx_transition_from_state ON transition_definition (workflow_id, from_state);

CREATE TABLE IF NOT EXISTS entity_workflow_instance (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id        UUID         NOT NULL,
    entity_type      VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(128) NOT NULL,
    workflow_id      UUID         NOT NULL REFERENCES workflow_definition (id),
    current_state    VARCHAR(64)  NOT NULL,
    state_entered_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_entity_instance UNIQUE (entity_id, entity_type, tenant_id)
);
CREATE INDEX idx_ewi_entity ON entity_workflow_instance (entity_id, entity_type);
CREATE INDEX idx_ewi_tenant ON entity_workflow_instance (tenant_id);
CREATE INDEX idx_ewi_state  ON entity_workflow_instance (tenant_id, current_state);

CREATE TABLE IF NOT EXISTS transition_history (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id     UUID         NOT NULL REFERENCES entity_workflow_instance (id) ON DELETE CASCADE,
    from_state      VARCHAR(64),
    to_state        VARCHAR(64)  NOT NULL,
    action_code     VARCHAR(64)  NOT NULL,
    actor_subject   VARCHAR(255),
    actor_role      VARCHAR(64),
    transition_type VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',
    comment         TEXT,
    transitioned_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    correlation_id  UUID
);
CREATE INDEX idx_th_instance     ON transition_history (instance_id);
CREATE INDEX idx_th_transitioned ON transition_history (transitioned_at DESC);
CREATE INDEX idx_th_correlation  ON transition_history (correlation_id) WHERE correlation_id IS NOT NULL;

-- Immutable history
CREATE RULE no_update_history AS ON UPDATE TO transition_history DO INSTEAD NOTHING;
CREATE RULE no_delete_history AS ON DELETE TO transition_history DO INSTEAD NOTHING;
