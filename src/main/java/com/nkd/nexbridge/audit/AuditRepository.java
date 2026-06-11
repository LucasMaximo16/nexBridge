package com.nkd.nexbridge.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    Optional<AuditEntry> findByTraceId(String traceId);

    List<AuditEntry> findByConsumerId(String consumerId);

    List<AuditEntry> findByResult(String result);

    Page<AuditEntry> findByResultOrderByTimestampUtcDesc(String result, Pageable pageable);

    Page<AuditEntry> findAllByOrderByTimestampUtcDesc(Pageable pageable);

    @Query("SELECT a FROM AuditEntry a WHERE " +
           "(:result IS NULL OR a.result = :result) AND " +
           "(:endpoint IS NULL OR a.endpoint LIKE %:endpoint%) AND " +
           "(:consumerId IS NULL OR a.consumerId = :consumerId) AND " +
           "(:from IS NULL OR a.timestampUtc >= :from) AND " +
           "(:to IS NULL OR a.timestampUtc <= :to) " +
           "ORDER BY a.timestampUtc DESC")
    Page<AuditEntry> findByFilters(
            @Param("result") String result,
            @Param("endpoint") String endpoint,
            @Param("consumerId") String consumerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditEntry a")
    long countAll();

    @Query("SELECT COUNT(a) FROM AuditEntry a WHERE a.result = :result")
    long countByResult(@Param("result") String result);

    @Query("SELECT AVG(a.durationMs) FROM AuditEntry a WHERE a.durationMs IS NOT NULL")
    Double avgDurationMs();

    @Query("SELECT COUNT(a) FROM AuditEntry a WHERE a.maskApplied = true")
    long countMasked();
}
