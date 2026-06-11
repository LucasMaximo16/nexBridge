CREATE TABLE consumer_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_id     VARCHAR(100) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    auth_type       VARCHAR(20) NOT NULL,
    api_key_hash    VARCHAR(256),
    allowed_paths   TEXT[],
    denied_fields   TEXT[],
    rate_limit      INTEGER DEFAULT 1000,
    active          BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ DEFAULT now()
);
