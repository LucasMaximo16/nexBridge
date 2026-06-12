package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.domain.ApiDefinition;
import com.nkd.nexbridge.domain.ApiDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/apis")
@RequiredArgsConstructor
public class AdminApiController {

    private final ApiDefinitionRepository apiRepository;
    private final NexBridgeProperties properties;

    @PostMapping
    public ResponseEntity<NexResponse<ApiDefinition>> create(@RequestBody ApiDefinition def) {
        ApiDefinition saved = apiRepository.save(def);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    @GetMapping
    public ResponseEntity<NexResponse<List<ApiDefinition>>> list() {
        return ResponseEntity.ok(NexResponse.ok(apiRepository.findAll(), buildMeta()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexResponse<ApiDefinition>> findById(@PathVariable UUID id) {
        Optional<ApiDefinition> def = apiRepository.findById(id);
        if (def.isEmpty()) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("API não encontrada").httpStatus(404).build(),
                    buildMeta()));
        }
        return ResponseEntity.ok(NexResponse.ok(def.get(), buildMeta()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<NexResponse<ApiDefinition>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        Optional<ApiDefinition> opt = apiRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder().code("NOT_FOUND").message("API não encontrada").httpStatus(404).build(),
                    buildMeta()));
        }
        ApiDefinition def = opt.get();
        def.setStatus(body.get("status"));
        return ResponseEntity.ok(NexResponse.ok(apiRepository.save(def), buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
