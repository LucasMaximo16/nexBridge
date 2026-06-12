package com.nkd.nexbridge.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
public class CrmDestination {

    public RoutingResult send(DestinationConfig dest, Map<String, Object> payload, String traceId) {
        long start = System.currentTimeMillis();
        int maxAttempts = dest.getRetryCount();
        long delay = dest.getRetryDelayMs();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RestClient.RequestBodySpec request = RestClient.create()
                        .post()
                        .uri(dest.getCrmEndpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .header("X-Integration-Source", "nexbridge");

                if (dest.getHeaders() != null) {
                    dest.getHeaders().forEach(request::header);
                }

                request.body(payload).retrieve().toBodilessEntity();
                long duration = System.currentTimeMillis() - start;
                return new RoutingResult(dest.getDestinationId(), dest.getType(), true, null, null, duration);
            } catch (Exception e) {
                lastException = e;
                log.warn("CRM attempt {}/{} failed for destination {}: {}", attempt, maxAttempts, dest.getDestinationId(), e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delay * (long) Math.pow(2, attempt - 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        return new RoutingResult(dest.getDestinationId(), dest.getType(), false,
                lastException != null ? lastException.getMessage() : "All attempts failed", null, duration);
    }
}
