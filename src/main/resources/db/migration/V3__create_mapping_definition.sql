CREATE TABLE mapping_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mapping_id      VARCHAR(100) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    description     VARCHAR(500),
    connector_id    VARCHAR(100) NOT NULL,
    copybook_id     VARCHAR(100),
    source_format   VARCHAR(20) NOT NULL,
    target_format   VARCHAR(20) NOT NULL,
    request_fields  JSONB NOT NULL,
    response_fields JSONB NOT NULL,
    active          BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ DEFAULT now(),
    UNIQUE(mapping_id, version)
);
