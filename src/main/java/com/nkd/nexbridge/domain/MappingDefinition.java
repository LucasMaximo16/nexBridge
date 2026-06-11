package com.nkd.nexbridge.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mapping_definition",
       uniqueConstraints = @UniqueConstraint(columnNames = {"mapping_id", "version"}))
@Data
@NoArgsConstructor
public class MappingDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mapping_id", nullable = false, length = 100)
    private String mappingId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(length = 500)
    private String description;

    @Column(name = "connector_id", nullable = false, length = 100)
    private String connectorId;

    @Column(name = "copybook_id", length = 100)
    private String copybookId;

    @Column(name = "source_format", nullable = false, length = 20)
    private String sourceFormat;

    @Column(name = "target_format", nullable = false, length = 20)
    private String targetFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_fields", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> requestFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_fields", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> responseFields;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
