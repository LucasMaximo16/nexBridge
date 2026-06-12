package com.nkd.nexbridge.api.controller;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.domain.ApiDefinitionRepository;
import com.nkd.nexbridge.domain.MappingDefinitionRepository;
import com.nkd.nexbridge.domain.MappingDefinition;
import com.nkd.nexbridge.domain.ApiDefinition;
import com.nkd.nexbridge.governance.ComplianceScorer;
import com.nkd.nexbridge.governance.DataLineageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class GovernanceController {

    private final ComplianceScorer complianceScorer;
    private final DataLineageService dataLineageService;
    private final NexBridgeProperties properties;
    private final MappingDefinitionRepository mappingRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;

    public record ImpactAnalysis(
            String connectorId,
            int mappingsAfetados,
            int apisAfetadas,
            int rotasAfetadas,
            List<String> mappingIds,
            List<String> apiPaths,
            String recomendacao
    ) {}

    @GetMapping("/compliance")
    public ResponseEntity<NexResponse<ComplianceScorer.ComplianceReport>> compliance() {
        log.info("GET /api/v1/compliance");
        ComplianceScorer.ComplianceReport report = complianceScorer.score();
        return ResponseEntity.ok(NexResponse.ok(report, buildMeta()));
    }

    @GetMapping("/lineage/{field}")
    public ResponseEntity<NexResponse<DataLineageService.DataLineage>> lineage(
            @PathVariable String field) {
        log.info("GET /api/v1/lineage/{}", field);
        DataLineageService.DataLineage lineage = dataLineageService.getLineage(field);
        return ResponseEntity.ok(NexResponse.ok(lineage, buildMeta()));
    }

    @GetMapping("/impact/{connectorId}")
    public ResponseEntity<NexResponse<ImpactAnalysis>> impact(@PathVariable String connectorId) {
        log.info("GET /api/v1/impact/{}", connectorId);

        List<MappingDefinition> mappings = mappingRepository.findByConnectorId(connectorId);
        List<ApiDefinition> apis = apiDefinitionRepository.findByConnectorId(connectorId);

        List<String> mappingIds = mappings.stream()
                .map(MappingDefinition::getMappingId)
                .distinct()
                .toList();

        List<String> apiPaths = apis.stream()
                .map(ApiDefinition::getPath)
                .distinct()
                .toList();

        int mappingsAfetados = mappingIds.size();
        int apisAfetadas = apiPaths.size();
        // Estimate routes via apis (each api = 1 route)
        int rotasAfetadas = apisAfetadas;

        String recomendacao;
        if (apisAfetadas >= 3) {
            recomendacao = "ALTO RISCO";
        } else if (apisAfetadas >= 1) {
            recomendacao = "MÉDIO RISCO";
        } else {
            recomendacao = "BAIXO RISCO";
        }

        ImpactAnalysis analysis = new ImpactAnalysis(
                connectorId,
                mappingsAfetados,
                apisAfetadas,
                rotasAfetadas,
                mappingIds,
                apiPaths,
                recomendacao
        );

        return ResponseEntity.ok(NexResponse.ok(analysis, buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
