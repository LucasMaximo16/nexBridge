package com.nkd.nexbridge.connector.totvs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkd.nexbridge.connector.*;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Conector TOTVS Protheus via API REST (RF-017).
 *
 * O Protheus expõe endpoints REST em: http://{host}:{port}/rest/{recurso}
 * Autenticação: Basic Auth ou Bearer token (configurável via vault://)
 * Operação enviada no campo "operation" do ConnectorRequest:
 *   "GET"    → GET /rest/{resource}?{params como query string}
 *   "POST"   → POST /rest/{resource} com body JSON dos params
 *   "PUT"    → PUT /rest/{resource}/{id}
 *   "DELETE" → DELETE /rest/{resource}/{id}
 */
@Slf4j
@RequiredArgsConstructor
public class TotvsProtheusConnector implements LegacyConnector {

    private final VaultService vaultService;
    private TotvsProtheusConnectorConfig config;
    private String connectorId;
    private HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();

        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) cfg.getOrDefault("connection", cfg);

        this.config = TotvsProtheusConnectorConfig.builder()
                .baseUrl((String) conn.getOrDefault("base_url", "http://localhost:8080/rest"))
                .username((String) conn.get("username"))
                .passwordRef((String) conn.get("password_ref"))
                .authType((String) conn.getOrDefault("auth_type", "BASIC"))
                .tokenRef((String) conn.get("token_ref"))
                .company((String) conn.getOrDefault("company", "01"))
                .branch((String) conn.getOrDefault("branch", "01"))
                .timeoutMs(((Number) conn.getOrDefault("timeout_ms", 10000)).intValue())
                .maxRetries(((Number) conn.getOrDefault("max_retries", 3)).intValue())
                .build();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .build();

        log.info("TotvsProtheusConnector initialized: {} baseUrl={}", connectorId, config.getBaseUrl());
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        long start = System.currentTimeMillis();
        String operation = request.getOperation() != null ? request.getOperation().toUpperCase() : "POST";
        Map<String, Object> params = request.getParams() != null ? request.getParams() : Map.of();

        try {
            String resource = (String) params.getOrDefault("_resource", "");
            String id = (String) params.getOrDefault("_id", null);
            String url = buildUrl(resource, id, operation, params);

            String authHeader = buildAuthHeader();
            String body = null;
            if ("POST".equals(operation) || "PUT".equals(operation)) {
                body = objectMapper.writeValueAsString(params);
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Trace-Id", request.getTraceId() != null ? request.getTraceId() : "")
                    .header("FilialAtiva", config.getBranch())
                    .header("EmpresaAtiva", config.getCompany());

            if (authHeader != null) {
                reqBuilder.header("Authorization", authHeader);
            }

            reqBuilder = switch (operation) {
                case "GET"    -> reqBuilder.GET();
                case "DELETE" -> reqBuilder.DELETE();
                case "PUT"    -> reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(body));
                default       -> reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
            };

            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            int duration = (int) (System.currentTimeMillis() - start);

            log.info("TotvsProtheusConnector[{}]: {} {} → HTTP {} ({}ms)",
                    connectorId, operation, url, response.statusCode(), duration);

            if (response.statusCode() >= 400) {
                throw new ConnectorException("TOTVS_HTTP_" + response.statusCode(),
                        "TOTVS Protheus returned HTTP " + response.statusCode() + ": " + response.body());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = response.body() != null && !response.body().isBlank()
                    ? objectMapper.readValue(response.body(), Map.class)
                    : Map.of("status", "ok");

            return ConnectorResponse.builder()
                    .success(true)
                    .textPayload(response.body())
                    .resultSet(resultMap)
                    .durationMs(duration)
                    .build();

        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("TOTVS_ERROR", "TOTVS Protheus error: " + e.getMessage(), e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            String authHeader = buildAuthHeader();
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/"))
                    .timeout(Duration.ofMillis(5000))
                    .GET();
            if (authHeader != null) reqBuilder.header("Authorization", authHeader);

            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            int latency = (int) (System.currentTimeMillis() - start);
            boolean ok = response.statusCode() < 500;
            return HealthStatus.builder().connectorId(connectorId).healthy(ok)
                    .message("TOTVS Protheus HTTP " + response.statusCode())
                    .latencyMs(latency).checkedAt(Instant.now()).build();
        } catch (Exception e) {
            return HealthStatus.builder().connectorId(connectorId).healthy(false)
                    .message("TOTVS unreachable: " + e.getMessage())
                    .latencyMs(0).checkedAt(Instant.now()).build();
        }
    }

    @Override public String getConnectorId() { return connectorId; }
    @Override public ConnectorType getType() { return ConnectorType.TOTVS_PROTHEUS; }
    @Override public void shutdown() { log.info("TotvsProtheusConnector[{}] shutdown", connectorId); }

    private String buildUrl(String resource, String id, String operation, Map<String, Object> params) {
        StringBuilder url = new StringBuilder(config.getBaseUrl());
        if (!resource.isBlank()) {
            url.append("/").append(resource);
        }
        if (id != null && !id.isBlank() && ("GET".equals(operation) || "PUT".equals(operation) || "DELETE".equals(operation))) {
            url.append("/").append(id);
        }
        if ("GET".equals(operation) && !params.isEmpty()) {
            StringBuilder query = new StringBuilder("?");
            params.forEach((k, v) -> {
                if (!k.startsWith("_")) {
                    query.append(k).append("=").append(v).append("&");
                }
            });
            if (query.length() > 1) {
                url.append(query.substring(0, query.length() - 1));
            }
        }
        return url.toString();
    }

    private String buildAuthHeader() {
        try {
            return switch (config.getAuthType().toUpperCase()) {
                case "BASIC" -> {
                    String password = vaultService.get(config.getPasswordRef());
                    String encoded = Base64.getEncoder().encodeToString(
                            (config.getUsername() + ":" + password).getBytes());
                    yield "Basic " + encoded;
                }
                case "BEARER" -> {
                    String token = vaultService.get(config.getTokenRef());
                    yield "Bearer " + token;
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("TotvsProtheusConnector[{}]: failed to build auth header: {}", connectorId, e.getMessage());
            return null;
        }
    }
}
