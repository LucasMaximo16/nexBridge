package com.nkd.nexbridge.api;

import com.nkd.nexbridge.domain.ApiDefinition;
import com.nkd.nexbridge.domain.MappingDefinition;
import com.nkd.nexbridge.domain.MappingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenApiGenerator {

    private final MappingDefinitionRepository mappingRepository;

    public Map<String, Object> generate(ApiDefinition api) {
        Optional<MappingDefinition> mappingOpt = mappingRepository
                .findByMappingIdAndVersion(api.getMappingId(), api.getMappingVersion());

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", buildInfo(api));
        spec.put("paths", buildPaths(api, mappingOpt.orElse(null)));
        spec.put("components", buildComponents(api, mappingOpt.orElse(null)));

        return spec;
    }

    private Map<String, Object> buildInfo(ApiDefinition api) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "NexBridge API — " + api.getPath());
        info.put("version", api.getVersion());
        info.put("description", "Auto-gerado pelo NexBridge Integration Fabric");
        return info;
    }

    private Map<String, Object> buildPaths(ApiDefinition api, MappingDefinition mapping) {
        String method = api.getMethod().toLowerCase();
        String resource = extractResource(api.getPath());

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", "Endpoint " + api.getMethod() + " " + api.getPath());
        operation.put("operationId", api.getMethod() + "_" + resource + "_" + api.getVersion());
        operation.put("security", List.of(Map.of("bearerAuth", List.of())));

        if ("post".equals(method) || "put".equals(method)) {
            operation.put("requestBody", buildRequestBody(mapping));
        }

        operation.put("responses", buildResponses());

        Map<String, Object> methodMap = new LinkedHashMap<>();
        methodMap.put(method, operation);

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put(api.getPath(), methodMap);

        return paths;
    }

    private Map<String, Object> buildRequestBody(MappingDefinition mapping) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        if (mapping == null) {
            schema.put("additionalProperties", true);
        } else {
            List<String> required = new ArrayList<>();
            Map<String, Object> properties = new LinkedHashMap<>();

            if (mapping.getRequestFields() != null) {
                for (Map<String, Object> field : mapping.getRequestFields()) {
                    String action = (String) field.getOrDefault("action", "MAP");
                    if ("DISCARD".equals(action) || "CONSTANT".equals(action)) continue;

                    String from = (String) field.get("from");
                    if (from == null) continue;

                    Map<String, Object> prop = new LinkedHashMap<>();
                    prop.put("type", inferType(from));
                    String auditNote = (String) field.get("auditNote");
                    prop.put("description", auditNote != null ? auditNote : "Campo de entrada");

                    if ("string".equals(prop.get("type")) && containsDateKeyword(from)) {
                        prop.put("format", "date");
                    }

                    properties.put(from, prop);

                    Boolean isRequired = (Boolean) field.get("required");
                    if (Boolean.TRUE.equals(isRequired)) {
                        required.add(from);
                    }
                }
            }

            if (!required.isEmpty()) schema.put("required", required);
            schema.put("properties", properties);
        }

        Map<String, Object> jsonContent = new LinkedHashMap<>();
        jsonContent.put("schema", schema);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("application/json", jsonContent);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", true);
        requestBody.put("content", content);

        return requestBody;
    }

    private Map<String, Object> buildResponses() {
        Map<String, Object> responses = new LinkedHashMap<>();

        // 200
        Map<String, Object> schemaRef = new LinkedHashMap<>();
        schemaRef.put("$ref", "#/components/schemas/NexResponse");
        Map<String, Object> jsonContent = new LinkedHashMap<>();
        jsonContent.put("schema", schemaRef);
        Map<String, Object> content200 = new LinkedHashMap<>();
        content200.put("application/json", jsonContent);
        Map<String, Object> r200 = new LinkedHashMap<>();
        r200.put("description", "Sucesso");
        r200.put("content", content200);
        responses.put("200", r200);

        // 400
        responses.put("400", Map.of("description", "Validação falhou"));
        // 401
        responses.put("401", Map.of("description", "Token JWT ausente ou inválido"));
        // 502
        responses.put("502", Map.of("description", "Erro no sistema legado"));

        return responses;
    }

    private Map<String, Object> buildComponents(ApiDefinition api, MappingDefinition mapping) {
        Map<String, Object> securitySchemes = new LinkedHashMap<>();
        Map<String, Object> bearerAuth = new LinkedHashMap<>();
        bearerAuth.put("type", "http");
        bearerAuth.put("scheme", "bearer");
        bearerAuth.put("bearerFormat", "JWT");
        securitySchemes.put("bearerAuth", bearerAuth);

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("NexResponse", buildNexResponseSchema());
        schemas.put("NexError", buildNexErrorSchema());
        schemas.put("NexMeta", buildNexMetaSchema());

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", securitySchemes);
        components.put("schemas", schemas);

        return components;
    }

    private Map<String, Object> buildNexResponseSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("success", Map.of("type", "boolean"));
        props.put("data", Map.of("type", "object"));
        props.put("error", Map.of("$ref", "#/components/schemas/NexError"));
        props.put("meta", Map.of("$ref", "#/components/schemas/NexMeta"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    private Map<String, Object> buildNexErrorSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("code", Map.of("type", "string"));
        props.put("message", Map.of("type", "string"));
        props.put("http_status", Map.of("type", "integer"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    private Map<String, Object> buildNexMetaSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("trace_id", Map.of("type", "string"));
        props.put("timestamp", Map.of("type", "string", "format", "date-time"));
        props.put("source_system", Map.of("type", "string"));
        props.put("source_connector", Map.of("type", "string"));
        props.put("copybook_version", Map.of("type", "string"));
        props.put("processing_ms", Map.of("type", "integer"));
        props.put("masked_fields", Map.of("type", "array", "items", Map.of("type", "string")));
        props.put("discarded_fields", Map.of("type", "array", "items", Map.of("type", "string")));
        props.put("nexbridge_version", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    private String inferType(String fieldName) {
        if (fieldName == null) return "string";
        String lower = fieldName.toLowerCase();
        if (lower.contains("valor") || lower.contains("saldo") || lower.contains("taxa")
                || lower.contains("parcela") || lower.contains("limite")) {
            return "number";
        }
        return "string";
    }

    private boolean containsDateKeyword(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.contains("data") || lower.contains("dt") || lower.contains("date");
    }

    private String extractResource(String path) {
        if (path == null) return "resource";
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty() && !parts[i].startsWith("{")) {
                return parts[i];
            }
        }
        return "resource";
    }
}
