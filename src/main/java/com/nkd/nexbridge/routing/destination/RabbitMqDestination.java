package com.nkd.nexbridge.routing.destination;

import com.nkd.nexbridge.security.VaultService;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Helper class (not a Spring bean) for sending messages to RabbitMQ via the Management API.
 * Uses POST /api/exchanges/{vhost}/{exchange}/publish (RabbitMQ Management HTTP API).
 */
public class RabbitMqDestination {

    private RabbitMqDestination() {}

    /**
     * Send a message to a RabbitMQ exchange via the Management API.
     *
     * @param config      destination config keys: host, port (default 5672), virtualHost (default "/"),
     *                    exchange, routingKey, username, passwordRef (vault:// ref)
     * @param data        payload to send (serialized to JSON string)
     * @param vaultService used to resolve passwordRef vault references
     */
    public static void send(Map<String, Object> config, Map<String, Object> data, VaultService vaultService) {
        String host = (String) config.get("host");
        int port = config.containsKey("port") ? ((Number) config.get("port")).intValue() : 5672;
        String virtualHost = config.containsKey("virtualHost") ? (String) config.get("virtualHost") : "/";
        String exchange = (String) config.get("exchange");
        String routingKey = config.containsKey("routingKey") ? (String) config.get("routingKey") : "";
        String username = (String) config.get("username");
        String passwordRef = (String) config.get("passwordRef");

        if (host == null || exchange == null || username == null || passwordRef == null) {
            throw new RuntimeException("RabbitMQ destination missing required config: host, exchange, username, passwordRef");
        }

        String password = vaultService.get(passwordRef);
        if (password == null) {
            throw new RuntimeException("RabbitMQ password could not be resolved from vault ref: " + passwordRef);
        }

        // Build JSON payload — serialize data map manually to avoid adding Jackson dependency here
        // (Jackson is available as Spring Boot includes it, but we use it via ObjectMapper in PayloadFormatter)
        String payloadJson = buildJsonPayload(data);

        // Escape for embedding inside the outer JSON string
        String escapedPayload = payloadJson
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        String escapedRoutingKey = routingKey.replace("\\", "\\\\").replace("\"", "\\\"");

        String body = "{\"properties\":{},\"routing_key\":\"" + escapedRoutingKey + "\","
                + "\"payload\":\"" + escapedPayload + "\","
                + "\"payload_encoding\":\"string\"}";

        // Encode virtualHost for URL (e.g. "/" → "%2F")
        String encodedVhost = URLEncoder.encode(virtualHost, StandardCharsets.UTF_8);
        String encodedExchange = URLEncoder.encode(exchange, StandardCharsets.UTF_8);

        // Use http; if port is 443 or 15671 use https
        String scheme = (port == 443 || port == 15671) ? "https" : "http";
        String url = scheme + "://" + host + ":" + port
                + "/api/exchanges/" + encodedVhost + "/" + encodedExchange + "/publish";

        String credentials = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + credentials)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("RabbitMQ Management API returned status " + response.statusCode()
                        + " for exchange=" + exchange);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to RabbitMQ exchange=" + exchange + ": " + e.getMessage(), e);
        }
    }

    private static String buildJsonPayload(Map<String, Object> data) {
        if (data == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return buildJsonPayload(map);
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
