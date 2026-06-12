package com.nkd.nexbridge.connector;

import com.nkd.nexbridge.domain.ConnectorDefinition;

public interface LegacyConnector {
    String getConnectorId();
    ConnectorType getType();
    ConnectorResponse send(ConnectorRequest request);
    HealthStatus healthCheck();
    void initialize(ConnectorDefinition config);
    void shutdown();
}
