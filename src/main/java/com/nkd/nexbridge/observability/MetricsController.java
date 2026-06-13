package com.nkd.nexbridge.observability;

import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.fabric.ResponseCacheService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MeterRegistry meterRegistry;
    private final ResponseCacheService responseCacheService;
    private final NexBridgeProperties properties;

    @GetMapping("/summary")
    public ResponseEntity<NexResponse<Map<String, Object>>> getSummary() {
        Counter totalCounter   = meterRegistry.find("nexbridge.requests.total").counter();
        Counter successCounter = meterRegistry.find("nexbridge.requests.success").counter();
        Counter errorCounter   = meterRegistry.find("nexbridge.requests.error").counter();
        Timer   latencyTimer   = meterRegistry.find("nexbridge.request.latency").timer();

        double requestsTotal   = totalCounter   != null ? totalCounter.count()   : 0.0;
        double requestsSuccess = successCounter != null ? successCounter.count() : 0.0;
        double requestsError   = errorCounter   != null ? errorCounter.count()   : 0.0;

        double p50 = latencyTimer != null ? latencyTimer.percentile(0.50, TimeUnit.MILLISECONDS) : 0.0;
        double p95 = latencyTimer != null ? latencyTimer.percentile(0.95, TimeUnit.MILLISECONDS) : 0.0;
        double p99 = latencyTimer != null ? latencyTimer.percentile(0.99, TimeUnit.MILLISECONDS) : 0.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requests.total",   requestsTotal);
        summary.put("requests.success", requestsSuccess);
        summary.put("requests.error",   requestsError);
        summary.put("latency.p50",      p50);
        summary.put("latency.p95",      p95);
        summary.put("latency.p99",      p99);
        summary.put("cache.size",       responseCacheService.size());

        NexMeta meta = NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();

        return ResponseEntity.ok(NexResponse.ok(summary, meta));
    }
}
