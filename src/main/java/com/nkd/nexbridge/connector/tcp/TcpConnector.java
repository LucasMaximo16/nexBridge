package com.nkd.nexbridge.connector.tcp;

import com.nkd.nexbridge.connector.ConnectorRequest;
import com.nkd.nexbridge.connector.ConnectorResponse;
import com.nkd.nexbridge.connector.ConnectorType;
import com.nkd.nexbridge.connector.HealthStatus;
import com.nkd.nexbridge.connector.LegacyConnector;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.security.MtlsConfig;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class TcpConnector implements LegacyConnector {

    private final VaultService vaultService;
    private final MtlsConfig mtlsConfig;

    private TcpConnectorConfig config;
    private TcpConnectionPool pool;
    private String connectorId;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();

        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) cfg.getOrDefault("connection", cfg);
        @SuppressWarnings("unchecked")
        Map<String, Object> tls = (Map<String, Object>) conn.getOrDefault("tls", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> poolCfg = (Map<String, Object>) conn.getOrDefault("pool", Map.of());

        boolean tlsEnabled = Boolean.TRUE.equals(tls.get("enabled"));

        this.config = TcpConnectorConfig.builder()
                .host((String) conn.getOrDefault("host", "localhost"))
                .port(((Number) conn.getOrDefault("port", 5000)).intValue())
                .tlsEnabled(tlsEnabled)
                .tlsVersion((String) tls.getOrDefault("version", "TLSv1.3"))
                .certPath(tlsEnabled ? mtlsConfig.getCertPath() : null)
                .keyPath(tlsEnabled ? mtlsConfig.getKeyPath() : null)
                .caPath(tlsEnabled ? mtlsConfig.getCaPath() : null)
                .verifyPeer(Boolean.TRUE.equals(tls.getOrDefault("verify_peer", true)))
                .poolMin(((Number) poolCfg.getOrDefault("min", 2)).intValue())
                .poolMax(((Number) poolCfg.getOrDefault("max", 10)).intValue())
                .timeoutMs(((Number) conn.getOrDefault("timeout_ms", 5000)).intValue())
                .build();

        this.pool = new TcpConnectionPool(this.config);
        log.info("TcpConnector initialized: {}:{}  TLS={}", config.getHost(), config.getPort(), tlsEnabled);
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        long start = System.currentTimeMillis();
        Socket socket = pool.acquire();
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in  = socket.getInputStream();

            byte[] payload = request.getRawPayload();
            if (payload == null || payload.length == 0) {
                throw new ConnectorException("EMPTY_PAYLOAD", "Payload TCP não pode ser vazio");
            }

            out.write(payload);
            out.flush();

            byte[] response = in.readAllBytes();
            int duration = (int) (System.currentTimeMillis() - start);

            return ConnectorResponse.builder()
                    .success(true)
                    .rawPayload(response)
                    .durationMs(duration)
                    .build();

        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            log.error("TcpConnector[{}] send error: {}", connectorId, e.getMessage());
            throw new ConnectorException("TCP_SEND_ERROR", e.getMessage(), e);
        } finally {
            pool.release(socket);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        Socket socket = null;
        try {
            socket = pool.acquire();
            boolean ok = socket.isConnected() && !socket.isClosed();
            return HealthStatus.builder()
                    .connectorId(connectorId)
                    .healthy(ok)
                    .message(ok ? "OK" : "Socket closed")
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
        } finally {
            if (socket != null) pool.release(socket);
        }
    }

    @Override
    public String getConnectorId() { return connectorId; }

    @Override
    public ConnectorType getType() { return ConnectorType.TCP; }

    @Override
    public void shutdown() {
        if (pool != null) pool.shutdown();
        log.info("TcpConnector[{}] shutdown", connectorId);
    }
}
