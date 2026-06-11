package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.audit.AuditEntry;
import com.nkd.nexbridge.audit.AuditService;
import com.nkd.nexbridge.audit.AuditStatsDto;
import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<NexResponse<Page<AuditEntry>>> findAll(
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String consumerId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int limite) {

        var pageable = PageRequest.of(pagina, Math.min(limite, 100));

        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;

        Page<AuditEntry> page = auditService.findByFilters(resultado, endpoint, consumerId, fromInstant, toInstant, pageable);

        return ResponseEntity.ok(NexResponse.ok(page, buildMeta()));
    }

    @GetMapping("/stats")
    public ResponseEntity<NexResponse<AuditStatsDto>> stats() {
        AuditStatsDto stats = auditService.getStats();
        return ResponseEntity.ok(NexResponse.ok(stats, buildMeta()));
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<NexResponse<AuditEntry>> findByTraceId(@PathVariable String traceId) {
        Optional<AuditEntry> entry = auditService.findByTraceId(traceId);
        if (entry.isEmpty()) {
            var error = NexError.builder()
                    .code("NOT_FOUND")
                    .message("Evento de auditoria não encontrado: " + traceId)
                    .httpStatus(404)
                    .build();
            return ResponseEntity.status(404).body(NexResponse.error(error, buildMeta()));
        }
        return ResponseEntity.ok(NexResponse.ok(entry.get(), buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion("1.0.0")
                .build();
    }
}
