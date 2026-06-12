package com.nkd.nexbridge.connector.soap;

import com.nkd.nexbridge.connector.*;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SoapConnector implements LegacyConnector {

    private final VaultService vaultService;
    private SoapConnectorConfig config;
    private String connectorId;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) cfg.getOrDefault("auth", Map.of());
        this.config = SoapConnectorConfig.builder()
                .wsdlUrl((String) cfg.get("wsdl_url"))
                .authType((String) auth.getOrDefault("type", "NONE"))
                .username((String) auth.get("username"))
                .passwordRef((String) auth.get("password_ref"))
                .timeoutMs(((Number) cfg.getOrDefault("timeout_ms", 5000)).intValue())
                .build();
        log.info("SoapConnector initialized: {}", connectorId);
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        long start = System.currentTimeMillis();
        try {
            String endpoint = config.getWsdlUrl().replace("?wsdl", "");
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(config.getTimeoutMs());
            conn.setReadTimeout(config.getTimeoutMs());
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", request.getOperation() != null ? request.getOperation() : "");

            if ("BASIC".equalsIgnoreCase(config.getAuthType()) && config.getUsername() != null) {
                String password = vaultService.get(config.getPasswordRef());
                String creds = Base64.getEncoder().encodeToString(
                        (config.getUsername() + ":" + password).getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + creds);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(request.getTextPayload().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            return ConnectorResponse.builder()
                    .success(status < 400).textPayload(response)
                    .durationMs((int) (System.currentTimeMillis() - start))
                    .errorCode(status >= 400 ? String.valueOf(status) : null)
                    .build();
        } catch (Exception e) {
            throw new ConnectorException("SOAP_ERROR", e.getMessage(), e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(config.getWsdlUrl()).openConnection();
            conn.setConnectTimeout(3000); conn.setReadTimeout(3000);
            int status = conn.getResponseCode();
            return HealthStatus.builder().connectorId(connectorId).healthy(status < 400)
                    .message("HTTP " + status).latencyMs(System.currentTimeMillis() - start).checkedAt(Instant.now()).build();
        } catch (Exception e) {
            return HealthStatus.builder().connectorId(connectorId).healthy(false)
                    .message(e.getMessage()).latencyMs(System.currentTimeMillis() - start).checkedAt(Instant.now()).build();
        }
    }

    @Override public String getConnectorId() { return connectorId; }
    @Override public ConnectorType getType() { return ConnectorType.SOAP; }
    @Override public void shutdown() { log.info("SoapConnector[{}] shutdown", connectorId); }
}
