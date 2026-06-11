package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${nexbridge.version:1.0.0}")
    private String version;

    @GetMapping("/health")
    public ResponseEntity<NexResponse<Map<String, Object>>> health() {
        var data = Map.<String, Object>of(
                "status", "UP",
                "version", version,
                "timestamp", Instant.now().toString()
        );
        var meta = NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(version)
                .build();
        return ResponseEntity.ok(NexResponse.ok(data, meta));
    }

    @GetMapping("/api/v1/status")
    public ResponseEntity<NexResponse<Map<String, Object>>> status() {
        var data = Map.<String, Object>of(
                "status", "OPERATIONAL",
                "version", version,
                "timestamp", Instant.now().toString()
        );
        var meta = NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(version)
                .build();
        return ResponseEntity.ok(NexResponse.ok(data, meta));
    }
}
