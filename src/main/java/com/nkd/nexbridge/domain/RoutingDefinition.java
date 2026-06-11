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
@Table(name = "routing_definition")
@Data
@NoArgsConstructor
public class RoutingDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "routing_id", unique = true, nullable = false, length = 100)
    private String routingId;

    @Column(name = "api_path", nullable = false, length = 200)
    private String apiPath;

    @Column(name = "api_method", nullable = false, length = 10)
    private String apiMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> destinations;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
