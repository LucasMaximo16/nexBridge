package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.domain.RoutingDefinition;
import com.nkd.nexbridge.domain.RoutingDefinitionRepository;
import com.nkd.nexbridge.exception.NexBridgeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/routing")
@RequiredArgsConstructor
public class AdminRoutingController {

    private final RoutingDefinitionRepository routingRepository;
    private final NexBridgeProperties properties;

    @GetMapping
    public ResponseEntity<NexResponse<List<RoutingDefinition>>> list() {
        return ResponseEntity.ok(NexResponse.ok(routingRepository.findAll(), buildMeta()));
    }

    @GetMapping("/{routingId}")
    public ResponseEntity<NexResponse<RoutingDefinition>> findByRoutingId(@PathVariable String routingId) {
        RoutingDefinition def = routingRepository.findByRoutingId(routingId)
                .orElseThrow(() -> new NexBridgeException("NOT_FOUND", "Roteamento não encontrado: " + routingId));
        return ResponseEntity.ok(NexResponse.ok(def, buildMeta()));
    }

    @PostMapping
    public ResponseEntity<NexResponse<RoutingDefinition>> create(@RequestBody RoutingDefinition def) {
        return ResponseEntity.ok(NexResponse.ok(routingRepository.save(def), buildMeta()));
    }

    @PutMapping("/{routingId}")
    public ResponseEntity<NexResponse<RoutingDefinition>> update(@PathVariable String routingId,
                                                                  @RequestBody RoutingDefinition body) {
        RoutingDefinition existing = routingRepository.findByRoutingId(routingId)
                .orElseThrow(() -> new NexBridgeException("NOT_FOUND", "Roteamento não encontrado: " + routingId));
        existing.setApiPath(body.getApiPath());
        existing.setApiMethod(body.getApiMethod());
        existing.setDestinations(body.getDestinations());
        existing.setActive(body.isActive());
        return ResponseEntity.ok(NexResponse.ok(routingRepository.save(existing), buildMeta()));
    }

    @DeleteMapping("/{routingId}")
    public ResponseEntity<NexResponse<Void>> deactivate(@PathVariable String routingId) {
        RoutingDefinition existing = routingRepository.findByRoutingId(routingId)
                .orElseThrow(() -> new NexBridgeException("NOT_FOUND", "Roteamento não encontrado: " + routingId));
        existing.setActive(false);
        routingRepository.save(existing);
        return ResponseEntity.ok(NexResponse.ok(null, buildMeta()));
    }

    @GetMapping("/active")
    public ResponseEntity<NexResponse<List<RoutingDefinition>>> listActive() {
        return ResponseEntity.ok(NexResponse.ok(routingRepository.findByActive(true), buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
