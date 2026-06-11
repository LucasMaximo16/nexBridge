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
@Table(name = "copybook_definition",
       uniqueConstraints = @UniqueConstraint(columnNames = {"copybook_id", "version"}))
@Data
@NoArgsConstructor
public class CopybookDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "copybook_id", nullable = false, length = 100)
    private String copybookId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "connector_id", nullable = false, length = 100)
    private String connectorId;

    @Column(length = 200)
    private String name;

    @Column(name = "raw_content", nullable = false, columnDefinition = "TEXT")
    private String rawContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_fields", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> parsedFields;

    @Column(name = "total_length", nullable = false)
    private int totalLength;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
