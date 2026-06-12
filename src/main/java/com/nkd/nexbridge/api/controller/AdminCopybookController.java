package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.domain.CopybookDefinition;
import com.nkd.nexbridge.domain.CopybookDefinitionRepository;
import com.nkd.nexbridge.mapper.copybook.CopybookField;
import com.nkd.nexbridge.mapper.copybook.CopybookParser;
import com.nkd.nexbridge.mapper.copybook.CopybookRegistry;
import com.nkd.nexbridge.config.NexBridgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/copybooks")
@RequiredArgsConstructor
public class AdminCopybookController {

    private final CopybookRegistry copybookRegistry;
    private final CopybookDefinitionRepository copybookRepository;
    private final CopybookParser copybookParser;
    private final NexBridgeProperties properties;

    @PostMapping
    public ResponseEntity<NexResponse<CopybookDefinition>> create(@RequestBody Map<String, Object> body) {
        String copybookId  = (String) body.get("copybook_id");
        String version     = (String) body.getOrDefault("version", "v1");
        String connectorId = (String) body.get("connector_id");
        String name        = (String) body.getOrDefault("name", copybookId);
        String rawContent  = (String) body.get("raw_content");

        CopybookDefinition saved = copybookRegistry.register(copybookId, version, connectorId, name, rawContent);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    @GetMapping
    public ResponseEntity<NexResponse<List<CopybookDefinition>>> list() {
        return ResponseEntity.ok(NexResponse.ok(copybookRepository.findAll(), buildMeta()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexResponse<CopybookDefinition>> findById(@PathVariable String id) {
        return copybookRepository.findAll().stream()
                .filter(c -> c.getCopybookId().equals(id) || c.getId().toString().equals(id))
                .findFirst()
                .map(c -> ResponseEntity.ok(NexResponse.ok(c, buildMeta())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/parse-preview")
    public ResponseEntity<NexResponse<List<CopybookField>>> preview(@RequestBody Map<String, String> body) {
        List<CopybookField> fields = copybookParser.parse(body.get("raw_content"));
        return ResponseEntity.ok(NexResponse.ok(fields, buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
