package com.nkd.nexbridge.connector.jdbc;

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

import java.sql.*;
import java.time.Instant;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class JdbcConnector implements LegacyConnector {

    private final VaultService vaultService;

    private JdbcConnectorConfig config;
    private String connectorId;
    private Connection connection;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) cfg.getOrDefault("connection", cfg);

        this.config = JdbcConnectorConfig.builder()
                .jdbcUrl((String) conn.get("jdbc_url"))
                .driverClass((String) conn.getOrDefault("driver", "org.postgresql.Driver"))
                .username((String) conn.get("username"))
                .passwordRef((String) conn.get("password_ref"))
                .build();

        log.info("JdbcConnector initialized: {}", connectorId);
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        long start = System.currentTimeMillis();
        try {
            Connection conn = getConnection();
            String query = request.getTextPayload();
            Map<String, Object> params = request.getParams() != null ? request.getParams() : Map.of();

            PreparedStatement stmt = conn.prepareStatement(query);
            int idx = 1;
            for (Object val : params.values()) {
                stmt.setObject(idx++, val);
            }

            Map<String, Object> resultSet = new LinkedHashMap<>();
            if (stmt.execute()) {
                ResultSet rs = stmt.getResultSet();
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                resultSet.put("rows", rows);
                resultSet.put("count", rows.size());
            }

            return ConnectorResponse.builder()
                    .success(true)
                    .resultSet(resultSet)
                    .durationMs((int) (System.currentTimeMillis() - start))
                    .build();

        } catch (Exception e) {
            log.error("JdbcConnector[{}] error: {}", connectorId, e.getMessage());
            throw new ConnectorException("JDBC_ERROR", e.getMessage(), e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            Connection conn = getConnection();
            boolean valid = conn.isValid(3);
            return HealthStatus.builder()
                    .connectorId(connectorId)
                    .healthy(valid)
                    .message(valid ? "OK" : "Connection invalid")
                    .latencyMs(System.currentTimeMillis() - start)
                    .checkedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return HealthStatus.builder()
                    .connectorId(connectorId)
                    .healthy(false)
                    .message(e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .checkedAt(Instant.now())
                    .build();
        }
    }

    private Connection getConnection() throws Exception {
        if (connection != null && !connection.isClosed()) return connection;
        String password = vaultService.get(config.getPasswordRef());
        Class.forName(config.getDriverClass());
        connection = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), password);
        return connection;
    }

    @Override
    public String getConnectorId() { return connectorId; }

    @Override
    public ConnectorType getType() { return ConnectorType.JDBC; }

    @Override
    public void shutdown() {
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
        log.info("JdbcConnector[{}] shutdown", connectorId);
    }
}
