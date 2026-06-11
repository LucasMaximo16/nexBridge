package com.nkd.nexbridge.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 50)
    private String traceId;

    @Column(name = "timestamp_utc", nullable = false)
    private Instant timestampUtc;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "endpoint", length = 500)
    private String endpoint;

    @Column(name = "consumer_id", length = 200)
    private String consumerId;

    @Column(name = "consumer_ip", columnDefinition = "INET")
    private String consumerIp;

    @Column(name = "source_system", length = 200)
    private String sourceSystem;

    @Column(name = "connector_id", length = 100)
    private String connectorId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "sensitive_fields", columnDefinition = "TEXT[]")
    private List<String> sensitiveFields;

    @Column(name = "mask_applied")
    private Boolean maskApplied;

    @Column(name = "discarded_fields", columnDefinition = "TEXT[]")
    private List<String> discardedFields;

    @Column(name = "result", length = 20)
    private String result;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "mapping_id", length = 100)
    private String mappingId;

    @Column(name = "copybook_version", length = 50)
    private String copybookVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "routing_results", columnDefinition = "jsonb")
    private List<Map<String, Object>> routingResults;

    @Column(name = "request_size_bytes")
    private Integer requestSizeBytes;

    @Column(name = "response_size_bytes")
    private Integer responseSizeBytes;
}
