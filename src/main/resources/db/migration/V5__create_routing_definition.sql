CREATE TABLE routing_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routing_id      VARCHAR(100) UNIQUE NOT NULL,
    api_path        VARCHAR(200) NOT NULL,
    api_method      VARCHAR(10) NOT NULL,
    destinations    JSONB NOT NULL,
    active          BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ DEFAULT now()
);
