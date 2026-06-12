package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.connector.ConnectorRegistry;
import com.nkd.nexbridge.connector.HealthStatus;
import com.nkd.nexbridge.connector.LegacyConnector;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.domain.ConnectorDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/connectors")
@RequiredArgsConstructor
public class AdminConnectorController {

    private final ConnectorDefinitionRepository connectorRepository;
    private final ConnectorRegistry connectorRegistry;
    private final NexBridgeProperties properties;

    @PostMapping
    public ResponseEntity<NexResponse<ConnectorDefinition>> create(@RequestBody ConnectorDefinition def) {
        ConnectorDefinition saved = connectorRepository.save(def);
        if (saved.isEnabled()) {
            try { connectorRegistry.register(saved); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    @GetMapping
    public ResponseEntity<NexResponse<List<ConnectorDefinition>>> list() {
        return ResponseEntity.ok(NexResponse.ok(connectorRepository.findAll(), buildMeta()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexResponse<ConnectorDefinition>> findById(@PathVariable UUID id) {
        Optional<ConnectorDefinition> def = connectorRepository.findById(id);
        if (def.isEmpty()) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Conector não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        return ResponseEntity.ok(NexResponse.ok(def.get(), buildMeta()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NexResponse<ConnectorDefinition>> update(@PathVariable UUID id,
                                                                    @RequestBody ConnectorDefinition body) {
        if (!connectorRepository.existsById(id)) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Conector não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        body.setId(id);
        ConnectorDefinition saved = connectorRepository.save(body);
        connectorRegistry.unregister(saved.getConnectorId());
        if (saved.isEnabled()) {
            try { connectorRegistry.register(saved); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<NexResponse<HealthStatus>> health(@PathVariable UUID id) {
        Optional<ConnectorDefinition> def = connectorRepository.findById(id);
        if (def.isEmpty()) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Conector não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        Optional<LegacyConnector> connector = connectorRegistry.find(def.get().getConnectorId());
        if (connector.isEmpty()) {
            HealthStatus offline = HealthStatus.builder()
                    .connectorId(def.get().getConnectorId())
                    .healthy(false).message("Conector não inicializado").checkedAt(Instant.now()).build();
            return ResponseEntity.ok(NexResponse.ok(offline, buildMeta()));
        }
        return ResponseEntity.ok(NexResponse.ok(connector.get().healthCheck(), buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
