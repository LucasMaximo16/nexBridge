package com.nkd.nexbridge.connector.sap;

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

@Slf4j
@RequiredArgsConstructor
public class SapRfcConnector implements LegacyConnector {

    private final VaultService vaultService;

    private SapRfcConnectorConfig config;
    private String connectorId;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();

        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) cfg.getOrDefault("connection", cfg);

        this.config = new SapRfcConnectorConfig();
        config.setHost((String) conn.getOrDefault("host", "localhost"));
        config.setSystemNumber((String) conn.getOrDefault("system_number", "00"));
        config.setClient((String) conn.getOrDefault("client", "100"));
        config.setUsername((String) conn.getOrDefault("username", ""));
        config.setPasswordRef((String) conn.getOrDefault("password_ref", ""));
        config.setLanguage((String) conn.getOrDefault("language", "PT"));

        // Resolve password via vault
        vaultService.get(config.getPasswordRef());

        log.info("SapRfcConnector[{}] initialized: {}:{}", connectorId, config.getHost(), config.getSystemNumber());
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        try {
            Map<String, Object> params = request.getParams() != null ? request.getParams() : Map.of();
            String bapi = (String) params.get("bapi");

            log.info("SapRfcConnector: calling BAPI {} on SAP {}", bapi, config.getHost());

            return ConnectorResponse.builder()
                    .success(true)
                    .resultSet(Map.of("return_code", "0", "message", "SAP RFC simulated"))
                    .build();

        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            log.error("SapRfcConnector[{}] error: {}", connectorId, e.getMessage());
            throw new ConnectorException("SAP_RFC_ERROR", e.getMessage(), e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        return HealthStatus.builder()
                .connectorId(connectorId)
                .healthy(true)
                .message("SAP RFC connector configured")
                .latencyMs(0)
                .checkedAt(Instant.now())
                .build();
    }

    @Override
    public String getConnectorId() { return connectorId; }

    @Override
    public ConnectorType getType() { return ConnectorType.SAP_RFC; }

    @Override
    public void shutdown() {
        log.info("SapRfcConnector[{}] shutdown", connectorId);
    }
}
