package com.nkd.nexbridge.routing.destination;

import com.nkd.nexbridge.security.VaultService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/**
 * Helper class (not a Spring bean) for sending messages to AWS SQS via
 * AWS Signature Version 4 signed HTTP requests — no AWS SDK required.
 */
public class SqsDestination {

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private SqsDestination() {}

    /**
     * Send a message to an SQS queue.
     *
     * @param config      destination config keys: queueUrl, region,
     *                    accessKeyRef (vault:// ref), secretKeyRef (vault:// ref)
     * @param data        payload serialized to JSON and URL-encoded as MessageBody
     * @param vaultService used to resolve vault references
     */
    public static void send(Map<String, Object> config, Map<String, Object> data, VaultService vaultService) {
        String queueUrl = (String) config.get("queueUrl");
        String region = (String) config.get("region");
        String accessKeyRef = (String) config.get("accessKeyRef");
        String secretKeyRef = (String) config.get("secretKeyRef");

        if (queueUrl == null || region == null || accessKeyRef == null || secretKeyRef == null) {
            throw new RuntimeException("SQS destination missing required config: queueUrl, region, accessKeyRef, secretKeyRef");
        }

        String accessKey = vaultService.get(accessKeyRef);
        String secretKey = vaultService.get(secretKeyRef);

        if (accessKey == null || secretKey == null) {
            throw new RuntimeException("SQS credentials could not be resolved from vault");
        }

        String messageBody = buildJsonPayload(data);

        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = now.format(DATE_TIME_FMT);
            String dateStamp = now.format(DATE_FMT);

            // Request body
            String requestBody = "Action=SendMessage"
                    + "&MessageBody=" + URLEncoder.encode(messageBody, StandardCharsets.UTF_8)
                    + "&Version=2012-11-05";

            // Parse host from queueUrl
            URI uri = URI.create(queueUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            if (path == null || path.isEmpty()) path = "/";

            // Canonical headers (must be sorted)
            String canonicalHeaders = "content-type:application/x-www-form-urlencoded\n"
                    + "host:" + host + "\n"
                    + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "content-type;host;x-amz-date";

            // Payload hash
            String payloadHash = sha256Hex(requestBody);

            // Canonical request
            String canonicalRequest = "POST\n"
                    + path + "\n"
                    + "\n"                        // no query string
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + payloadHash;

            // String to sign
            String algorithm = "AWS4-HMAC-SHA256";
            String credentialScope = dateStamp + "/" + region + "/sqs/aws4_request";
            String stringToSign = algorithm + "\n"
                    + amzDate + "\n"
                    + credentialScope + "\n"
                    + sha256Hex(canonicalRequest);

            // Signing key
            byte[] signingKey = getSigningKey(secretKey, dateStamp, region, "sqs");

            // Signature
            String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));

            // Authorization header
            String authHeader = algorithm + " Credential=" + accessKey + "/" + credentialScope
                    + ", SignedHeaders=" + signedHeaders
                    + ", Signature=" + signature;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(queueUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Host", host)
                    .header("X-Amz-Date", amzDate)
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("SQS returned status " + response.statusCode()
                        + " for queue=" + queueUrl + ". Response: " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to SQS queue=" + queueUrl + ": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // AWS Sig V4 helpers
    // -------------------------------------------------------------------------

    private static byte[] getSigningKey(String secretKey, String date, String region, String service) throws Exception {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, date);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    // -------------------------------------------------------------------------
    // Simple JSON serialization (no external lib)
    // -------------------------------------------------------------------------

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
