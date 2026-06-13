package com.nkd.nexbridge.fieldmapper;

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
import java.util.*;

@RestController
@RequestMapping("/api/admin/mapping-editor")
@RequiredArgsConstructor
public class MappingEditorController {

    private final MappingDefinitionRepository mappingRepository;
    private final NexBridgeProperties properties;

    // GET /{mappingId}/{version}/fields
    @GetMapping("/{mappingId}/{version}/fields")
    public ResponseEntity<NexResponse<Map<String, Object>>> getFields(
            @PathVariable String mappingId,
            @PathVariable String version) {

        Optional<MappingDefinition> opt = mappingRepository.findByMappingIdAndVersion(mappingId, version);
        if (opt.isEmpty()) {
            return notFound();
        }
        MappingDefinition def = opt.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestFields", def.getRequestFields());
        body.put("responseFields", def.getResponseFields());
        return ResponseEntity.ok(NexResponse.ok(body, buildMeta()));
    }

    // POST /{mappingId}/{version}/fields/request
    @PostMapping("/{mappingId}/{version}/fields/request")
    public ResponseEntity<NexResponse<MappingDefinition>> addOrUpdateRequestField(
            @PathVariable String mappingId,
            @PathVariable String version,
            @RequestBody Map<String, Object> fieldDef) {

        Optional<MappingDefinition> opt = mappingRepository.findByMappingIdAndVersion(mappingId, version);
        if (opt.isEmpty()) {
            return notFound();
        }
        MappingDefinition def = opt.get();
        upsertField(def.getRequestFields(), fieldDef);
        MappingDefinition saved = mappingRepository.save(def);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    // POST /{mappingId}/{version}/fields/response
    @PostMapping("/{mappingId}/{version}/fields/response")
    public ResponseEntity<NexResponse<MappingDefinition>> addOrUpdateResponseField(
            @PathVariable String mappingId,
            @PathVariable String version,
            @RequestBody Map<String, Object> fieldDef) {

        Optional<MappingDefinition> opt = mappingRepository.findByMappingIdAndVersion(mappingId, version);
        if (opt.isEmpty()) {
            return notFound();
        }
        MappingDefinition def = opt.get();
        upsertField(def.getResponseFields(), fieldDef);
        MappingDefinition saved = mappingRepository.save(def);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    // DELETE /{mappingId}/{version}/fields/request/{fieldName}
    @DeleteMapping("/{mappingId}/{version}/fields/request/{fieldName}")
    public ResponseEntity<NexResponse<MappingDefinition>> deleteRequestField(
            @PathVariable String mappingId,
            @PathVariable String version,
            @PathVariable String fieldName) {

        Optional<MappingDefinition> opt = mappingRepository.findByMappingIdAndVersion(mappingId, version);
        if (opt.isEmpty()) {
            return notFound();
        }
        MappingDefinition def = opt.get();
        def.getRequestFields().removeIf(f -> fieldName.equals(f.get("name")));
        MappingDefinition saved = mappingRepository.save(def);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    // DELETE /{mappingId}/{version}/fields/response/{fieldName}
    @DeleteMapping("/{mappingId}/{version}/fields/response/{fieldName}")
    public ResponseEntity<NexResponse<MappingDefinition>> deleteResponseField(
            @PathVariable String mappingId,
            @PathVariable String version,
            @PathVariable String fieldName) {

        Optional<MappingDefinition> opt = mappingRepository.findByMappingIdAndVersion(mappingId, version);
        if (opt.isEmpty()) {
            return notFound();
        }
        MappingDefinition def = opt.get();
        def.getResponseFields().removeIf(f -> fieldName.equals(f.get("name")));
        MappingDefinition saved = mappingRepository.save(def);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    // POST /{mappingId}/{version}/validate
    @PostMapping("/{mappingId}/{version}/validate")
    public ResponseEntity<NexResponse<Map<String, Object>>> validate(
            @PathVariable String mappingId,
            @PathVariable String version) {

        Optional<MappingDefinition> opt = mappingRepository.findByMappingIdAndVersion(mappingId, version);
        if (opt.isEmpty()) {
            return notFound();
        }
        MappingDefinition def = opt.get();
        List<String> issues = new ArrayList<>();

        // Rule 1: All MAP actions must have a non-blank target
        List<Map<String, Object>> allFields = new ArrayList<>();
        if (def.getRequestFields() != null) allFields.addAll(def.getRequestFields());
        if (def.getResponseFields() != null) allFields.addAll(def.getResponseFields());

        Set<String> seenSources = new LinkedHashSet<>();
        for (Map<String, Object> field : allFields) {
            String name = Objects.toString(field.get("name"), "");
            String action = Objects.toString(field.get("action"), "");

            // Check MAP action has target
            if ("MAP".equalsIgnoreCase(action)) {
                Object target = field.get("target");
                if (target == null || target.toString().isBlank()) {
                    issues.add("Field '" + name + "' has action MAP but no target defined.");
                }
            }

            // Check duplicate source fields
            if (!name.isBlank()) {
                if (!seenSources.add(name)) {
                    issues.add("Duplicate source field detected: '" + name + "'.");
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", issues.isEmpty());
        result.put("issues", issues);
        return ResponseEntity.ok(NexResponse.ok(result, buildMeta()));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Upsert a field into the list, matching by "name". */
    private void upsertField(List<Map<String, Object>> fields, Map<String, Object> fieldDef) {
        String name = Objects.toString(fieldDef.get("name"), "");
        if (!name.isBlank()) {
            fields.removeIf(f -> name.equals(f.get("name")));
        }
        fields.add(new LinkedHashMap<>(fieldDef));
    }

    private <T> ResponseEntity<NexResponse<T>> notFound() {
        return ResponseEntity.status(404).body(NexResponse.error(
                NexError.builder()
                        .code("NOT_FOUND")
                        .message("MappingDefinition not found")
                        .httpStatus(404)
                        .build(),
                buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
