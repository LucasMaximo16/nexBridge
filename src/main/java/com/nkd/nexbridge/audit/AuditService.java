package com.nkd.nexbridge.audit;

import com.nkd.nexbridge.api.filter.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditRepository auditRepository;

    /**
     * Grava evento de auditoria. Propagation.REQUIRES_NEW garante que o log
     * é gravado mesmo se a transação principal fizer rollback.
     * NUNCA lançar exceção daqui — não pode interromper o fluxo principal.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditEntry entry) {
        try {
            if (entry.getTimestampUtc() == null) {
                entry.setTimestampUtc(Instant.now());
            }
            if (entry.getTraceId() == null) {
                entry.setTraceId(TraceIdFilter.current());
            }
            auditRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist audit entry traceId={}: {}", entry.getTraceId(), e.getMessage());
        }
    }

    public void saveSync(AuditEntry entry) {
        try {
            if (entry.getTimestampUtc() == null) {
                entry.setTimestampUtc(Instant.now());
            }
            if (entry.getTraceId() == null) {
                entry.setTraceId(TraceIdFilter.current());
            }
            auditRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist audit entry traceId={}: {}", entry.getTraceId(), e.getMessage());
        }
    }

    public Optional<AuditEntry> findByTraceId(String traceId) {
        return auditRepository.findByTraceId(traceId);
    }

    public Page<AuditEntry> findByFilters(String result, String endpoint, String consumerId,
                                           Instant from, Instant to, Pageable pageable) {
        return auditRepository.findByFilters(result, endpoint, consumerId, from, to, pageable);
    }

    public AuditStatsDto getStats() {
        long total = auditRepository.countAll();
        long success = auditRepository.countByResult("SUCCESS");
        long error = auditRepository.countByResult("ERROR");
        long warning = auditRepository.countByResult("WARNING");
        long blocked = auditRepository.countByResult("BLOCKED");
        Double avgLatency = auditRepository.avgDurationMs();
        long masked = auditRepository.countMasked();

        return AuditStatsDto.builder()
                .total(total)
                .sucesso(success)
                .erro(error)
                .aviso(warning)
                .bloqueado(blocked)
                .latenciaMediaMs(avgLatency != null ? avgLatency.longValue() : 0L)
                .dadosMascarados(masked)
                .dadosExpostos(0L)
                .build();
    }
}
