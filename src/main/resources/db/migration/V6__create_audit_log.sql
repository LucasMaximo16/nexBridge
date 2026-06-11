CREATE TABLE audit_log (
    id               BIGSERIAL,
    trace_id         VARCHAR(50) NOT NULL,
    timestamp_utc    TIMESTAMPTZ NOT NULL DEFAULT now(),
    method           VARCHAR(10),
    endpoint         VARCHAR(500),
    consumer_id      VARCHAR(200),
    consumer_ip      INET,
    source_system    VARCHAR(200),
    connector_id     VARCHAR(100),
    http_status      INTEGER,
    duration_ms      INTEGER,
    sensitive_fields TEXT[],
    mask_applied     BOOLEAN DEFAULT false,
    discarded_fields TEXT[],
    result           VARCHAR(20),
    error_code       VARCHAR(100),
    error_message    TEXT,
    mapping_id       VARCHAR(100),
    copybook_version VARCHAR(50),
    routing_results  JSONB,
    request_size_bytes  INTEGER,
    response_size_bytes INTEGER
) PARTITION BY RANGE (timestamp_utc);

CREATE TABLE audit_log_y2024
    PARTITION OF audit_log
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE audit_log_y2025
    PARTITION OF audit_log
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

CREATE TABLE audit_log_y2026
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE INDEX idx_audit_trace    ON audit_log(trace_id);
CREATE INDEX idx_audit_ts       ON audit_log(timestamp_utc DESC);
CREATE INDEX idx_audit_consumer ON audit_log(consumer_id);
CREATE INDEX idx_audit_result   ON audit_log(result);

CREATE RULE audit_no_update AS ON UPDATE TO audit_log DO INSTEAD NOTHING;
CREATE RULE audit_no_delete AS ON DELETE TO audit_log DO INSTEAD NOTHING;
