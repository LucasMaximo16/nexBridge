package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.domain.ApiDefinition;
import com.nkd.nexbridge.domain.ApiDefinitionRepository;
import com.nkd.nexbridge.exception.NexBridgeException;
import com.nkd.nexbridge.fabric.FlowContext;
import com.nkd.nexbridge.fabric.IntegrationFabric;
import com.nkd.nexbridge.fabric.RouteDefinition;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DataController {

    private final ApiDefinitionRepository apiDefinitionRepository;
    private final IntegrationFabric integrationFabric;
    private final NexBridgeProperties properties;

    @PostMapping("/api/{version}/{resource}")
    public ResponseEntity<NexResponse<Map<String, Object>>> post(
            @PathVariable String version,
            @PathVariable String resource,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return execute("/api/" + version + "/" + resource, "POST", body, request);
    }

    @GetMapping("/api/{version}/{resource}")
    public ResponseEntity<NexResponse<Map<String, Object>>> get(
            @PathVariable String version,
            @PathVariable String resource,
            @RequestParam Map<String, Object> params,
            HttpServletRequest request) {
        return execute("/api/" + version + "/" + resource, "GET", params, request);
    }

    @GetMapping("/api/{version}/{resource}/{id}")
    public ResponseEntity<NexResponse<Map<String, Object>>> getById(
            @PathVariable String version,
            @PathVariable String resource,
            @PathVariable String id,
            HttpServletRequest request) {
        return execute("/api/" + version + "/" + resource + "/" + id, "GET", Map.of("id", id), request);
    }

    @PutMapping("/api/{version}/{resource}/{id}")
    public ResponseEntity<NexResponse<Map<String, Object>>> put(
            @PathVariable String version,
            @PathVariable String resource,
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        body.put("id", id);
        return execute("/api/" + version + "/" + resource + "/" + id, "PUT", body, request);
    }

    private ResponseEntity<NexResponse<Map<String, Object>>> execute(
            String path, String method, Map<String, Object> data, HttpServletRequest request) {

        String consumerId = resolveConsumerId(request);
        String consumerIp = request.getRemoteAddr();

        // Find matching API definition — try exact path first, then wildcard
        Optional<ApiDefinition> apiDef = apiDefinitionRepository
                .findByPathAndMethodAndVersion(path, method, extractVersion(path))
                .or(() -> apiDefinitionRepository.findByStatus("LIVE").stream()
                        .filter(d -> d.getMethod().equals(method) && pathMatches(d.getPath(), path))
                        .findFirst());

        if (apiDef.isEmpty()) {
            var error = NexError.builder()
                    .code("ROUTE_NOT_FOUND")
                    .message("Nenhuma API configurada para: " + method + " " + path)
                    .httpStatus(404)
                    .build();
            return ResponseEntity.status(404).body(NexResponse.error(error, buildMeta(null)));
        }

        ApiDefinition api = apiDef.get();
        if (!"LIVE".equals(api.getStatus()) && !"BETA".equals(api.getStatus())) {
            var error = NexError.builder()
                    .code("API_NOT_LIVE")
                    .message("API não está ativa. Status: " + api.getStatus())
                    .httpStatus(503)
                    .build();
            return ResponseEntity.status(503).body(NexResponse.error(error, buildMeta(null)));
        }

        RouteDefinition route = RouteDefinition.builder()
                .path(api.getPath())
                .method(api.getMethod())
                .version(api.getVersion())
                .connectorId(api.getConnectorId())
                .mappingId(api.getMappingId())
                .mappingVersion(api.getMappingVersion())
                .authMethod(api.getAuthMethod())
                .rateLimitPerMinute(api.getRateLimit())
                .cacheTtlSec(api.getCacheTtlSec())
                .status(api.getStatus())
                .build();

        try {
            FlowContext ctx = integrationFabric.execute(route, data, consumerId, consumerIp);
            return ResponseEntity.ok(NexResponse.ok(ctx.getResponseData(), buildMeta(ctx)));
        } catch (NexBridgeException e) {
            var error = NexError.builder()
                    .code(e.getErrorCode())
                    .message(e.getMessage())
                    .httpStatus(500)
                    .build();
            return ResponseEntity.status(500).body(NexResponse.error(error, buildMeta(null)));
        }
    }

    private String resolveConsumerId(HttpServletRequest request) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String p) return p;
        return request.getRemoteAddr();
    }

    private String extractVersion(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("api") && i + 1 < parts.length) return parts[i + 1];
        }
        return "v1";
    }

    private boolean pathMatches(String pattern, String actual) {
        String regex = pattern.replaceAll("\\{[^}]+}", "[^/]+");
        return actual.matches(regex);
    }

    private NexMeta buildMeta(FlowContext ctx) {
        NexMeta.NexMetaBuilder b = NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion());
        if (ctx != null) {
            b.sourceSystem(ctx.getSourceSystem())
             .sourceConnector(ctx.getConnectorId())
             .copybookVersion(ctx.getCopybookId())
             .processingMs(ctx.getDurationMs())
             .maskedFields(ctx.getMaskedFields())
             .discardedFields(ctx.getDiscardedFields());
        }
        return b.build();
    }
}
