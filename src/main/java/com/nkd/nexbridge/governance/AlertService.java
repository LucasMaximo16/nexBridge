package com.nkd.nexbridge.governance;

import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "cpf", "cnpj", "conta", "salario", "password", "token", "secret", "key"
    );

    private final AlertConfig alertConfig;
    private final VaultService vaultService;

    public void sendAlert(AlertLevel level, String title, String message, Map<String, String> context) {
        if (!alertConfig.isEnabled()) {
            return;
        }

        String contextText = buildContextText(context);

        if (alertConfig.getSlackWebhookUrl() != null && !alertConfig.getSlackWebhookUrl().isBlank()) {
            sendSlack(level, title, message, contextText);
        }

        if (alertConfig.isEmailEnabled() && alertConfig.getEmailTo() != null && !alertConfig.getEmailTo().isEmpty()) {
            sendEmail(level, title, message, contextText);
        }

        if (alertConfig.isPagerdutyEnabled()
                && alertConfig.getPagerdutyRoutingKey() != null
                && !alertConfig.getPagerdutyRoutingKey().isBlank()) {
            sendPagerDuty(level, title, message, context);
        }
    }

    // -------------------------------------------------------------------------
    // Slack
    // -------------------------------------------------------------------------

    private void sendSlack(AlertLevel level, String title, String message, String contextText) {
        try {
            String text = "[" + level.name() + "] " + title + "\n" + message + "\n" + contextText;
            // Escape for JSON string
            String escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "");
            String channel = alertConfig.getSlackChannel() != null ? alertConfig.getSlackChannel() : "#nexbridge-alerts";
            String escapedChannel = channel.replace("\"", "\\\"");

            String json = "{\"channel\":\"" + escapedChannel + "\",\"text\":\"" + escapedText + "\"}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(alertConfig.getSlackWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Slack alert returned non-2xx status: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to send Slack alert: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Email via raw SMTP socket
    // -------------------------------------------------------------------------

    private void sendEmail(AlertLevel level, String title, String message, String contextText) {
        try {
            String smtpPassword = null;
            if (alertConfig.getSmtpPasswordRef() != null && !alertConfig.getSmtpPasswordRef().isBlank()) {
                smtpPassword = vaultService.get(alertConfig.getSmtpPasswordRef());
            }

            String subject = "[NexBridge][" + level.name() + "] " + title;
            String body = message + "\r\n\r\n" + contextText;

            String from = alertConfig.getSmtpUser() != null ? alertConfig.getSmtpUser() : "nexbridge@localhost";

            try (Socket socket = new Socket(alertConfig.getSmtpHost(), alertConfig.getSmtpPort())) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream()));

                // Read greeting
                in.readLine();

                smtpCmd(out, in, "EHLO nexbridge\r\n");

                if (alertConfig.getSmtpUser() != null && !alertConfig.getSmtpUser().isBlank()
                        && smtpPassword != null) {
                    smtpCmd(out, in, "AUTH LOGIN\r\n");
                    smtpCmd(out, in, Base64.getEncoder()
                            .encodeToString(alertConfig.getSmtpUser().getBytes(StandardCharsets.UTF_8)) + "\r\n");
                    smtpCmd(out, in, Base64.getEncoder()
                            .encodeToString(smtpPassword.getBytes(StandardCharsets.UTF_8)) + "\r\n");
                }

                smtpCmd(out, in, "MAIL FROM:<" + from + ">\r\n");

                for (String to : alertConfig.getEmailTo()) {
                    smtpCmd(out, in, "RCPT TO:<" + to + ">\r\n");
                }

                smtpCmd(out, in, "DATA\r\n");

                String emailData = "From: " + from + "\r\n"
                        + "To: " + String.join(", ", alertConfig.getEmailTo()) + "\r\n"
                        + "Subject: " + subject + "\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n"
                        + "\r\n"
                        + body + "\r\n"
                        + ".\r\n";
                out.write(emailData.getBytes(StandardCharsets.UTF_8));
                out.flush();
                in.readLine(); // read response to DATA body

                smtpCmd(out, in, "QUIT\r\n");
            }
        } catch (Exception e) {
            log.warn("Failed to send email alert: {}", e.getMessage());
        }
    }

    private void smtpCmd(OutputStream out, java.io.BufferedReader in, String cmd) throws Exception {
        out.write(cmd.getBytes(StandardCharsets.UTF_8));
        out.flush();
        in.readLine();
    }

    // -------------------------------------------------------------------------
    // PagerDuty
    // -------------------------------------------------------------------------

    private void sendPagerDuty(AlertLevel level, String title, String message, Map<String, String> context) {
        try {
            String severity = switch (level) {
                case CRITICAL -> "critical";
                case WARNING -> "warning";
                case INFO -> "info";
            };

            // Build custom_details with only keys (no values for safety)
            String customDetails = buildCustomDetailsJson(context);

            String json = "{"
                    + "\"routing_key\":\"" + escapeJson(alertConfig.getPagerdutyRoutingKey()) + "\","
                    + "\"event_action\":\"trigger\","
                    + "\"payload\":{"
                    + "\"summary\":\"" + escapeJson(title) + "\","
                    + "\"severity\":\"" + severity + "\","
                    + "\"source\":\"NexBridge\","
                    + "\"custom_details\":" + customDetails
                    + "}"
                    + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://events.pagerduty.com/v2/enqueue"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("PagerDuty alert returned non-2xx status: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to send PagerDuty alert: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build context text for messages. Log only keys, never values.
     */
    private String buildContextText(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        return context.entrySet().stream()
                .map(e -> e.getKey() + "=" + (isSensitiveKey(e.getKey()) ? "***" : e.getValue()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Build JSON object for PagerDuty custom_details. Mask sensitive values.
     */
    private String buildCustomDetailsJson(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : context.entrySet()) {
            if (!first) sb.append(",");
            String value = isSensitiveKey(e.getKey()) ? "***MASKED***" : e.getValue();
            sb.append("\"").append(escapeJson(e.getKey())).append("\":\"").append(escapeJson(value)).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
