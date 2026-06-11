CREATE TABLE api_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    path            VARCHAR(200) NOT NULL,
    method          VARCHAR(10) NOT NULL,
    version         VARCHAR(10) NOT NULL,
    connector_id    VARCHAR(100) NOT NULL,
    mapping_id      VARCHAR(100) NOT NULL,
    mapping_version VARCHAR(20) NOT NULL,
    auth_method     VARCHAR(20) NOT NULL,
    rate_limit      INTEGER DEFAULT 1000,
    cache_ttl_sec   INTEGER DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'DRAFT',
    openapi_spec    JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    UNIQUE(path, method, version)
);
