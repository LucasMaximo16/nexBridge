package com.nkd.nexbridge.discovery;

import com.nkd.nexbridge.discovery.scanner.CobolProgramScanner;
import com.nkd.nexbridge.discovery.scanner.JclJobScanner;
import com.nkd.nexbridge.discovery.scanner.ShadowIntegrationScanner;
import com.nkd.nexbridge.discovery.scanner.VsamDatasetScanner;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.domain.ConnectorDefinitionRepository;
import com.nkd.nexbridge.domain.CopybookDefinition;
import com.nkd.nexbridge.domain.CopybookDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoveryService {

    private final ConnectorDefinitionRepository connectorRepository;
    private final CopybookDefinitionRepository copybookRepository;
    private final CobolProgramScanner cobolProgramScanner;
    private final VsamDatasetScanner vsamDatasetScanner;
    private final JclJobScanner jclJobScanner;
    private final ShadowIntegrationScanner shadowIntegrationScanner;

    public DiscoveryResult discover(String sistemaParam) {
        try {
            log.info("DiscoveryService: iniciando discovery para sistema '{}'", sistemaParam);

            List<ConnectorDefinition> connectors = resolveConnectors(sistemaParam);
            log.info("DiscoveryService: {} conectores encontrados para '{}'", connectors.size(), sistemaParam);

            Set<String> programasCobol = new LinkedHashSet<>();
            Set<String> datasetsVsam = new LinkedHashSet<>();
            Set<String> jclNames = new LinkedHashSet<>();
            Set<String> shadowIntegrations = new LinkedHashSet<>();

            for (ConnectorDefinition connector : connectors) {
                Map<String, Object> config = connector.getConfig();
                String connId = connector.getConnectorId();

                log.info("DiscoveryService: escaneando conector '{}'", connId);

                programasCobol.addAll(cobolProgramScanner.scan(connId, config));
                datasetsVsam.addAll(vsamDatasetScanner.scan(connId, config));

                List<JclJobScanner.JclJob> jobs = jclJobScanner.scan(connId, config);
                for (JclJobScanner.JclJob job : jobs) {
                    if (job.name() != null) {
                        jclNames.add(job.name());
                    }
                }

                shadowIntegrations.addAll(shadowIntegrationScanner.scan(connId, config));
            }

            int totalLinhasCobol = computeTotalCobolLines(connectors);
            log.info("DiscoveryService: total de linhas COBOL aproximado: {}", totalLinhasCobol);

            List<String> dadosSensiveis = collectSensitiveFields(connectors);
            log.info("DiscoveryService: {} campos sensíveis detectados", dadosSensiveis.size());

            DiscoveryResult result = DiscoveryResult.builder()
                    .sistema(sistemaParam)
                    .programasCobol(new ArrayList<>(programasCobol))
                    .datasetsVsam(new ArrayList<>(datasetsVsam))
                    .jcls(new ArrayList<>(jclNames))
                    .shadowIntegrations(new ArrayList<>(shadowIntegrations))
                    .dadosSensiveis(dadosSensiveis)
                    .totalProgramas(programasCobol.size())
                    .totalLinhasCobol(totalLinhasCobol)
                    .scannedAt(Instant.now())
                    .build();

            log.info("DiscoveryService: discovery concluído para '{}'", sistemaParam);
            return result;

        } catch (Exception e) {
            log.warn("DiscoveryService: erro durante discovery para '{}': {}", sistemaParam, e.getMessage());
            return DiscoveryResult.builder()
                    .sistema(sistemaParam)
                    .programasCobol(List.of())
                    .datasetsVsam(List.of())
                    .jcls(List.of())
                    .shadowIntegrations(List.of())
                    .dadosSensiveis(List.of())
                    .totalProgramas(0)
                    .totalLinhasCobol(0)
                    .scannedAt(Instant.now())
                    .build();
        }
    }

    private List<ConnectorDefinition> resolveConnectors(String sistemaParam) {
        try {
            if ("mainframe".equalsIgnoreCase(sistemaParam)) {
                List<ConnectorDefinition> all = connectorRepository.findAll();
                return all.stream()
                        .filter(c -> "TCP".equals(c.getType()) || "JDBC".equals(c.getType()) || "IBM_MQ".equals(c.getType()))
                        .toList();
            } else if ("as400".equalsIgnoreCase(sistemaParam)) {
                List<ConnectorDefinition> all = connectorRepository.findAll();
                return all.stream()
                        .filter(c -> "JDBC".equals(c.getType()) || "SOAP".equals(c.getType()) || "FILE".equals(c.getType()))
                        .toList();
            } else {
                return connectorRepository.findByConnectorId(sistemaParam)
                        .map(List::of)
                        .orElse(List.of());
            }
        } catch (Exception e) {
            log.warn("DiscoveryService: erro ao resolver conectores para '{}': {}", sistemaParam, e.getMessage());
            return List.of();
        }
    }

    private int computeTotalCobolLines(List<ConnectorDefinition> connectors) {
        try {
            return connectors.stream()
                    .flatMap(c -> copybookRepository.findByConnectorId(c.getConnectorId()).stream())
                    .mapToInt(CopybookDefinition::getTotalLength)
                    .sum();
        } catch (Exception e) {
            log.warn("DiscoveryService: erro ao computar linhas COBOL: {}", e.getMessage());
            return 0;
        }
    }

    private List<String> collectSensitiveFields(List<ConnectorDefinition> connectors) {
        try {
            List<String> sensitive = new ArrayList<>();
            for (ConnectorDefinition connector : connectors) {
                List<CopybookDefinition> copybooks = copybookRepository.findByConnectorId(connector.getConnectorId());
                for (CopybookDefinition copybook : copybooks) {
                    if (copybook.getParsedFields() == null) continue;
                    for (Map<String, Object> field : copybook.getParsedFields()) {
                        Object sensitiveFlag = field.get("sensitive");
                        if (Boolean.TRUE.equals(sensitiveFlag)) {
                            Object cobolName = field.get("cobolName");
                            Object name = field.get("name");
                            if (cobolName != null) {
                                sensitive.add(name + " (" + cobolName + ")");
                            } else if (name != null) {
                                sensitive.add(name.toString());
                            }
                        }
                    }
                }
            }
            return sensitive;
        } catch (Exception e) {
            log.warn("DiscoveryService: erro ao coletar campos sensíveis: {}", e.getMessage());
            return List.of();
        }
    }
}
