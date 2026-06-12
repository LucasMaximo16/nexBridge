package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.domain.RoutingDefinition;
import com.nkd.nexbridge.domain.RoutingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/routing")
@RequiredArgsConstructor
public class AdminRoutingController {

    private final RoutingDefinitionRepository routingRepository;
    private final NexBridgeProperties properties;

    @PostMapping
    public ResponseEntity<NexResponse<RoutingDefinition>> create(@RequestBody RoutingDefinition def) {
        return ResponseEntity.ok(NexResponse.ok(routingRepository.save(def), buildMeta()));
    }

    @GetMapping
    public ResponseEntity<NexResponse<List<RoutingDefinition>>> list() {
        return ResponseEntity.ok(NexResponse.ok(routingRepository.findAll(), buildMeta()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexResponse<RoutingDefinition>> findById(@PathVariable UUID id) {
        Optional<RoutingDefinition> def = routingRepository.findById(id);
        if (def.isEmpty()) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Roteamento não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        return ResponseEntity.ok(NexResponse.ok(def.get(), buildMeta()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NexResponse<RoutingDefinition>> update(@PathVariable UUID id,
                                                                   @RequestBody RoutingDefinition body) {
        if (!routingRepository.existsById(id)) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Roteamento não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        body.setId(id);
        return ResponseEntity.ok(NexResponse.ok(routingRepository.save(body), buildMeta()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<NexResponse<Void>> delete(@PathVariable UUID id) {
        if (!routingRepository.existsById(id)) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Roteamento não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        routingRepository.deleteById(id);
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
