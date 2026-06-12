package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.domain.ConsumerDefinition;
import com.nkd.nexbridge.domain.ConsumerDefinitionRepository;
import com.nkd.nexbridge.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin/consumers")
@RequiredArgsConstructor
@Slf4j
public class AdminConsumerController {

    private final ConsumerDefinitionRepository consumerRepository;
    private final JwtService jwtService;
    private final NexBridgeProperties properties;

    public record ConsumerWithToken(ConsumerDefinition consumer, String token) {}

    @PostMapping
    public ResponseEntity<NexResponse<ConsumerWithToken>> create(
            @RequestBody ConsumerDefinition body) {
        log.info("POST /api/v1/admin/consumers — name={}", body.getName());
        ConsumerDefinition saved = consumerRepository.save(body);
        String token = jwtService.generateToken(saved);
        ConsumerWithToken result = new ConsumerWithToken(saved, token);
        return ResponseEntity.status(201).body(NexResponse.ok(result, buildMeta()));
    }

    @GetMapping
    public ResponseEntity<NexResponse<List<ConsumerDefinition>>> listAll() {
        log.info("GET /api/v1/admin/consumers");
        List<ConsumerDefinition> consumers = consumerRepository.findAll();
        return ResponseEntity.ok(NexResponse.ok(consumers, buildMeta()));
    }

    @GetMapping("/{consumerId}")
    public ResponseEntity<NexResponse<ConsumerDefinition>> findById(
            @PathVariable String consumerId) {
        log.info("GET /api/v1/admin/consumers/{}", consumerId);
        Optional<ConsumerDefinition> opt = consumerRepository.findByConsumerId(consumerId);
        if (opt.isEmpty()) {
            return notFound(consumerId);
        }
        return ResponseEntity.ok(NexResponse.ok(opt.get(), buildMeta()));
    }

    @PutMapping("/{consumerId}")
    public ResponseEntity<NexResponse<ConsumerDefinition>> update(
            @PathVariable String consumerId,
            @RequestBody ConsumerDefinition body) {
        log.info("PUT /api/v1/admin/consumers/{}", consumerId);
        Optional<ConsumerDefinition> opt = consumerRepository.findByConsumerId(consumerId);
        if (opt.isEmpty()) {
            return notFound(consumerId);
        }
        ConsumerDefinition existing = opt.get();
        if (body.getName() != null) existing.setName(body.getName());
        if (body.getAllowedPaths() != null) existing.setAllowedPaths(body.getAllowedPaths());
        if (body.getDeniedFields() != null) existing.setDeniedFields(body.getDeniedFields());
        if (body.getRateLimit() > 0) existing.setRateLimit(body.getRateLimit());
        existing.setActive(body.isActive());
        ConsumerDefinition saved = consumerRepository.save(existing);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    @DeleteMapping("/{consumerId}")
    public ResponseEntity<NexResponse<ConsumerDefinition>> deactivate(
            @PathVariable String consumerId) {
        log.info("DELETE /api/v1/admin/consumers/{} — desativando", consumerId);
        Optional<ConsumerDefinition> opt = consumerRepository.findByConsumerId(consumerId);
        if (opt.isEmpty()) {
            return notFound(consumerId);
        }
        ConsumerDefinition existing = opt.get();
        existing.setActive(false);
        ConsumerDefinition saved = consumerRepository.save(existing);
        return ResponseEntity.ok(NexResponse.ok(saved, buildMeta()));
    }

    @PostMapping("/{consumerId}/token")
    public ResponseEntity<NexResponse<Map<String, String>>> generateToken(
            @PathVariable String consumerId) {
        log.info("POST /api/v1/admin/consumers/{}/token", consumerId);
        Optional<ConsumerDefinition> opt = consumerRepository.findByConsumerId(consumerId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(NexResponse.error(
                    NexError.builder()
                            .code("NOT_FOUND")
                            .message("Consumer não encontrado: " + consumerId)
                            .httpStatus(404)
                            .build(),
                    buildMeta()
            ));
        }
        String token = jwtService.generateToken(opt.get());
        return ResponseEntity.ok(NexResponse.ok(Map.of("token", token), buildMeta()));
    }

    private <T> ResponseEntity<NexResponse<T>> notFound(String consumerId) {
        NexError error = NexError.builder()
                .code("NOT_FOUND")
                .message("Consumer não encontrado: " + consumerId)
                .httpStatus(404)
                .build();
        return ResponseEntity.status(404).body(NexResponse.error(error, buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
