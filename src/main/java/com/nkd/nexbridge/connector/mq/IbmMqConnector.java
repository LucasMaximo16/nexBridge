package com.nkd.nexbridge.connector.mq;

import com.nkd.nexbridge.connector.*;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class IbmMqConnector implements LegacyConnector {

    private final VaultService vaultService;
    private IbmMqConnectorConfig config;
    private String connectorId;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) cfg.getOrDefault("connection", cfg);
        @SuppressWarnings("unchecked")
        Map<String, Object> queues = (Map<String, Object>) cfg.getOrDefault("queues", Map.of());
        this.config = IbmMqConnectorConfig.builder()
                .host((String) conn.getOrDefault("host", "localhost"))
                .port(((Number) conn.getOrDefault("port", 1414)).intValue())
                .channel((String) conn.getOrDefault("channel", "NXB.SVRCONN"))
                .queueManager((String) conn.getOrDefault("queue_manager", "QM1"))
                .username((String) conn.get("username"))
                .passwordRef((String) conn.get("password_ref"))
                .requestQueue((String) queues.getOrDefault("request", "NXB.IN.Q"))
                .responseQueue((String) queues.getOrDefault("response", "NXB.OUT.Q"))
                .timeoutMs(((Number) queues.getOrDefault("timeout_ms", 5000)).intValue())
                .build();
        log.info("IbmMqConnector initialized: {}", connectorId);
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        log.warn("IbmMqConnector[{}] IBM MQ client not configured — stub mode", connectorId);
        return ConnectorResponse.builder().success(false)
                .errorCode("MQ_NOT_CONFIGURED")
                .errorMessage("IBM MQ client library not available in this environment")
                .durationMs(0).build();
    }

    @Override
    public HealthStatus healthCheck() {
        return HealthStatus.builder().connectorId(connectorId).healthy(false)
                .message("IBM MQ client not configured — stub mode")
                .latencyMs(0).checkedAt(Instant.now()).build();
    }

    @Override public String getConnectorId() { return connectorId; }
    @Override public ConnectorType getType() { return ConnectorType.IBM_MQ; }
    @Override public void shutdown() { log.info("IbmMqConnector[{}] shutdown", connectorId); }
}
