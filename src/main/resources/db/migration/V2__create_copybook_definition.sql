CREATE TABLE copybook_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    copybook_id     VARCHAR(100) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    connector_id    VARCHAR(100) NOT NULL,
    name            VARCHAR(200),
    raw_content     TEXT NOT NULL,
    parsed_fields   JSONB NOT NULL,
    total_length    INTEGER NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    UNIQUE(copybook_id, version)
);
