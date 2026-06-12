package com.nkd.nexbridge.connector;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Builder
@Data
public class HealthStatus {
    private String connectorId;
    private boolean healthy;
    private String message;
    private long latencyMs;
    private Instant checkedAt;
}
