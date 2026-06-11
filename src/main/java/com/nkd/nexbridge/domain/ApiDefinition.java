package com.nkd.nexbridge.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "api_definition",
       uniqueConstraints = @UniqueConstraint(columnNames = {"path", "method", "version"}))
@Data
@NoArgsConstructor
public class ApiDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 10)
    private String version;

    @Column(name = "connector_id", nullable = false, length = 100)
    private String connectorId;

    @Column(name = "mapping_id", nullable = false, length = 100)
    private String mappingId;

    @Column(name = "mapping_version", nullable = false, length = 20)
    private String mappingVersion;

    @Column(name = "auth_method", nullable = false, length = 20)
    private String authMethod;

    @Column(name = "rate_limit")
    private int rateLimit = 1000;

    @Column(name = "cache_ttl_sec")
    private int cacheTtlSec = 0;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "openapi_spec", columnDefinition = "jsonb")
    private Map<String, Object> openapiSpec;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
