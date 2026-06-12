package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.domain.MappingDefinition;
import com.nkd.nexbridge.domain.MappingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/mappings")
@RequiredArgsConstructor
public class AdminMappingController {

    private final MappingDefinitionRepository mappingRepository;
    private final NexBridgeProperties properties;

    @PostMapping
    public ResponseEntity<NexResponse<MappingDefinition>> create(@RequestBody MappingDefinition def) {
        return ResponseEntity.ok(NexResponse.ok(mappingRepository.save(def), buildMeta()));
    }

    @GetMapping
    public ResponseEntity<NexResponse<List<MappingDefinition>>> list() {
        return ResponseEntity.ok(NexResponse.ok(mappingRepository.findAll(), buildMeta()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexResponse<MappingDefinition>> findById(@PathVariable UUID id) {
        Optional<MappingDefinition> def = mappingRepository.findById(id);
        if (def.isEmpty()) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Mapeamento não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        return ResponseEntity.ok(NexResponse.ok(def.get(), buildMeta()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NexResponse<MappingDefinition>> update(@PathVariable UUID id,
                                                                   @RequestBody MappingDefinition body) {
        if (!mappingRepository.existsById(id)) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("Mapeamento não encontrado").httpStatus(404).build(),
                    buildMeta()));
        }
        body.setId(id);
        return ResponseEntity.ok(NexResponse.ok(mappingRepository.save(body), buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
