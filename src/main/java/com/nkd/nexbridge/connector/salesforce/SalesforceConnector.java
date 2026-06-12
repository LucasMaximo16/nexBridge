package com.nkd.nexbridge.connector.salesforce;

import com.nkd.nexbridge.connector.ConnectorRequest;
import com.nkd.nexbridge.connector.ConnectorResponse;
import com.nkd.nexbridge.connector.ConnectorType;
import com.nkd.nexbridge.connector.HealthStatus;
import com.nkd.nexbridge.connector.LegacyConnector;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SalesforceConnector implements LegacyConnector {

    private final VaultService vaultService;

    private SalesforceConnectorConfig config;
    private String connectorId;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();

        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) cfg.getOrDefault("auth", cfg);
        @SuppressWarnings("unchecked")
        Map<String, Object> sync = (Map<String, Object>) cfg.getOrDefault("sync", Map.of());

        this.config = new SalesforceConnectorConfig();
        config.setClientIdRef((String) auth.getOrDefault("client_id_ref", ""));
        config.setClientSecretRef((String) auth.getOrDefault("client_secret_ref", ""));
        config.setTokenUrl((String) auth.getOrDefault("token_url",
                "https://login.salesforce.com/services/oauth2/token"));
        config.setInstanceUrl((String) auth.getOrDefault("instance_url", ""));
        config.setAuthType((String) auth.getOrDefault("auth_type", "OAUTH2_CLIENT_CREDENTIALS"));
        config.setDirection((String) sync.getOrDefault("direction", "BIDIRECTIONAL"));
        config.setPushOnEvent(Boolean.TRUE.equals(sync.getOrDefault("push_on_event", false)));
        config.setPullIntervalSec(((Number) sync.getOrDefault("pull_interval_sec", 60)).intValue());

        // Resolve credentials via vault — não faz conexão no initialize, apenas configura
        vaultService.get(config.getClientIdRef());
        vaultService.get(config.getClientSecretRef());

        log.info("SalesforceConnector[{}] initialized", connectorId);
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        try {
            Map<String, Object> params = request.getParams() != null ? request.getParams() : Map.of();
            String object = (String) params.get("object");
            String operation = (String) params.get("operation");

            log.info("SalesforceConnector: sending {} {} to Salesforce", operation, object);

            String sfId = "SF-SIMULATED-" + UUID.randomUUID().toString().substring(0, 8);

            return ConnectorResponse.builder()
                    .success(true)
                    .resultSet(Map.of("sf_id", sfId))
                    .build();

        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            log.error("SalesforceConnector[{}] error: {}", connectorId, e.getMessage());
            throw new ConnectorException("SF_ERROR", e.getMessage(), e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        return HealthStatus.builder()
                .connectorId(connectorId)
                .healthy(true)
                .message("Salesforce connector configured")
                .latencyMs(0)
                .checkedAt(Instant.now())
                .build();
    }

    @Override
    public String getConnectorId() { return connectorId; }

    @Override
    public ConnectorType getType() { return ConnectorType.SALESFORCE; }

    @Override
    public void shutdown() {
        log.info("SalesforceConnector[{}] shutdown", connectorId);
    }
}
