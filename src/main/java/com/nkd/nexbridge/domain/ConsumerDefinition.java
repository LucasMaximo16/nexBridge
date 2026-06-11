package com.nkd.nexbridge.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "consumer_definition")
@Data
@NoArgsConstructor
public class ConsumerDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consumer_id", unique = true, nullable = false, length = 100)
    private String consumerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "auth_type", nullable = false, length = 20)
    private String authType;

    @Column(name = "api_key_hash", length = 256)
    private String apiKeyHash;

    @Column(name = "allowed_paths", columnDefinition = "TEXT[]")
    private List<String> allowedPaths;

    @Column(name = "denied_fields", columnDefinition = "TEXT[]")
    private List<String> deniedFields;

    @Column(name = "rate_limit")
    private int rateLimit = 1000;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
