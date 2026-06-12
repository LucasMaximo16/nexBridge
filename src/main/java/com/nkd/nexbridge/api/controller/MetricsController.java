package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.audit.AuditRepository;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.connector.ConnectorRegistry;
import com.nkd.nexbridge.connector.HealthStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MetricsController {

    private final AuditRepository auditRepository;
    private final ConnectorRegistry connectorRegistry;
    private final NexBridgeProperties properties;

    public record MetricsSummary(
            long totalRequests,
            long successRequests,
            long errorRequests,
            double avgLatencyMs,
            long maskedDataCount,
            int activeConnectors,
            String uptime,
            Instant collectedAt
    ) {}

    public record IntegrationInfo(
            String connectorId,
            String type,
            boolean healthy,
            String message
    ) {}

    /**
     * GET /api/v1/metrics — público (permitAll no SecurityConfig)
     */
    @GetMapping("/metrics")
    public ResponseEntity<NexResponse<MetricsSummary>> getMetrics() {
        long totalRequests = auditRepository.countAll();
        long successRequests = auditRepository.countByResult("SUCCESS");
        long errorRequests = auditRepository.countByResult("ERROR");
        Double avgLatencyRaw = auditRepository.avgDurationMs();
        double avgLatencyMs = avgLatencyRaw != null ? avgLatencyRaw : 0.0;
        long maskedDataCount = auditRepository.countMasked();
        int activeConnectors = connectorRegistry.findAll().size();

        MetricsSummary summary = new MetricsSummary(
                totalRequests,
                successRequests,
                errorRequests,
                avgLatencyMs,
                maskedDataCount,
                activeConnectors,
                "Session active",
                Instant.now()
        );

        NexMeta meta = NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();

        return ResponseEntity.ok(NexResponse.ok(summary, meta));
    }

    /**
     * GET /api/v1/integrations — requer JWT
     */
    @GetMapping("/integrations")
    public ResponseEntity<NexResponse<List<IntegrationInfo>>> getIntegrations() {
        List<IntegrationInfo> integrations = connectorRegistry.findAll().stream()
                .map(connector -> {
                    HealthStatus health = connector.healthCheck();
                    return new IntegrationInfo(
                            connector.getConnectorId(),
                            connector.getType().name(),
                            health.isHealthy(),
                            health.getMessage()
                    );
                })
                .toList();

        NexMeta meta = NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();

        return ResponseEntity.ok(NexResponse.ok(integrations, meta));
    }
}
