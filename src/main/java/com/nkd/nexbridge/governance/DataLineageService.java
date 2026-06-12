package com.nkd.nexbridge.governance;

import com.nkd.nexbridge.audit.AuditRepository;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.domain.ConnectorDefinitionRepository;
import com.nkd.nexbridge.domain.CopybookDefinitionRepository;
import com.nkd.nexbridge.domain.MappingDefinition;
import com.nkd.nexbridge.domain.MappingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataLineageService {

    private final MappingDefinitionRepository mappingRepository;
    private final CopybookDefinitionRepository copybookRepository;
    private final ConnectorDefinitionRepository connectorRepository;
    private final AuditRepository auditRepository;

    public record DataLineage(String field, List<LineageStep> lineage) {}

    public record LineageStep(
            int step,
            String system,
            String fieldName,
            String operation,
            boolean masked,
            boolean origin
    ) {}

    public DataLineage getLineage(String fieldName) {
        log.info("Buscando lineage para campo: {}", fieldName);
        List<MappingDefinition> mappings = mappingRepository.findAll();
        List<LineageStep> steps = new ArrayList<>();
        int stepCounter = 0;
        boolean anyMasked = false;

        for (MappingDefinition mapping : mappings) {
            boolean found = false;
            String legacyFieldName = null;
            String operation = null;
            boolean masked = false;

            // Check requestFields
            if (mapping.getRequestFields() != null) {
                for (Map<String, Object> field : mapping.getRequestFields()) {
                    String from = fieldStr(field, "from");
                    String to = fieldStr(field, "to");
                    if (matchesField(fieldName, from) || matchesField(fieldName, to)) {
                        found = true;
                        legacyFieldName = to != null ? to : from;
                        operation = fieldStr(field, "transform");
                        masked = isMasked(operation, legacyFieldName, mapping.getCopybookId());
                        break;
                    }
                }
            }

            // Check responseFields if not found yet
            if (!found && mapping.getResponseFields() != null) {
                for (Map<String, Object> field : mapping.getResponseFields()) {
                    String from = fieldStr(field, "from");
                    String to = fieldStr(field, "to");
                    if (matchesField(fieldName, from) || matchesField(fieldName, to)) {
                        found = true;
                        legacyFieldName = to != null ? to : from;
                        operation = fieldStr(field, "transform");
                        masked = isMasked(operation, legacyFieldName, mapping.getCopybookId());
                        break;
                    }
                }
            }

            if (found) {
                boolean isOrigin = stepCounter == 0;
                if (masked) anyMasked = true;

                String systemName = resolveConnectorName(mapping.getConnectorId());

                steps.add(new LineageStep(
                        ++stepCounter,
                        systemName,
                        legacyFieldName != null ? legacyFieldName : fieldName,
                        operation,
                        masked,
                        isOrigin
                ));
            }
        }

        if (anyMasked) {
            steps.add(new LineageStep(
                    ++stepCounter,
                    "NexBridge Transformer",
                    fieldName,
                    "MASK",
                    true,
                    false
            ));
        }

        return new DataLineage(fieldName, steps);
    }

    private boolean matchesField(String searched, String candidate) {
        return candidate != null && candidate.equalsIgnoreCase(searched);
    }

    private boolean isMasked(String transform, String fieldName, String copybookId) {
        if (transform != null && transform.toUpperCase().contains("MASK")) {
            return true;
        }
        // Check copybook for sensitive fields
        if (copybookId != null) {
            return copybookRepository.findByCopybookId(copybookId).stream()
                    .anyMatch(cb -> {
                        if (cb.getParsedFields() == null) return false;
                        return cb.getParsedFields().stream().anyMatch(f -> {
                            String name = fieldStr(f, "name");
                            Object sensitive = f.get("sensitive");
                            return name != null && name.equalsIgnoreCase(fieldName)
                                    && Boolean.TRUE.equals(sensitive);
                        });
                    });
        }
        return false;
    }

    private String resolveConnectorName(String connectorId) {
        if (connectorId == null) return "Unknown";
        Optional<ConnectorDefinition> conn = connectorRepository.findByConnectorId(connectorId);
        return conn.map(ConnectorDefinition::getName).orElse(connectorId);
    }

    private String fieldStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
