CREATE TABLE connector_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id    VARCHAR(100) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    config          JSONB NOT NULL,
    enabled         BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_connector_type ON connector_definition(type);
CREATE INDEX idx_connector_enabled ON connector_definition(enabled);
