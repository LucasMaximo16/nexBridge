package com.nkd.nexbridge.discovery;

import com.nkd.nexbridge.discovery.scanner.CobolProgramScanner;
import com.nkd.nexbridge.discovery.scanner.JclJobScanner;
import com.nkd.nexbridge.discovery.scanner.ShadowIntegrationScanner;
import com.nkd.nexbridge.discovery.scanner.VsamDatasetScanner;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.domain.ConnectorDefinitionRepository;
import com.nkd.nexbridge.domain.CopybookDefinition;
import com.nkd.nexbridge.domain.CopybookDefinitionRepository;
import com.nkd.nexbridge.domain.MappingDefinition;
import com.nkd.nexbridge.domain.MappingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoveryService {

    private final ConnectorDefinitionRepository connectorRepository;
    private final CopybookDefinitionRepository copybookRepository;
    private final MappingDefinitionRepository mappingRepository;
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
                jclJobScanner.scan(connId, config).stream()
                        .filter(j -> j.name() != null)
                        .map(JclJobScanner.JclJob::name)
                        .forEach(jclNames::add);
                shadowIntegrations.addAll(shadowIntegrationScanner.scan(connId, config));
            }

            int totalLinhasCobol = computeTotalCobolLines(connectors);
            List<String> dadosSensiveis = collectSensitiveFields(connectors);
            log.info("DiscoveryService: discovery concluído — {} programas, {} campos sensíveis", programasCobol.size(), dadosSensiveis.size());

            return DiscoveryResult.builder()
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

    public List<JclJobScanner.JclJob> discoverJobs(String sistemaParam) {
        List<ConnectorDefinition> connectors = resolveConnectors(sistemaParam);
        List<JclJobScanner.JclJob> allJobs = new ArrayList<>();
        for (ConnectorDefinition connector : connectors) {
            allJobs.addAll(jclJobScanner.scan(connector.getConnectorId(), connector.getConfig()));
        }
        return allJobs;
    }

    // RF-008: Mapa de dependências entre sistemas
    public DependencyGraph buildDependencyGraph() {
        log.info("DiscoveryService: construindo grafo de dependências entre sistemas");

        List<ConnectorDefinition> allConnectors = connectorRepository.findAll();
        List<MappingDefinition> allMappings = mappingRepository.findAll();

        // Nó "NexBridge" central
        List<SystemNode> nodes = new ArrayList<>();
        List<SystemDependency> edges = new ArrayList<>();

        nodes.add(new SystemNode(
                "nexbridge",
                "INTEGRATION_FABRIC",
                "NexBridge",
                List.of(),
                List.of(),
                List.of()
        ));

        // Um nó por conector legado
        for (ConnectorDefinition connector : allConnectors) {
            Map<String, Object> config = connector.getConfig();
            String connId = connector.getConnectorId();

            List<String> programs = cobolProgramScanner.scan(connId, config);
            List<String> datasets = vsamDatasetScanner.scan(connId, config);
            List<String> sensitive = collectSensitiveFields(List.of(connector));

            nodes.add(new SystemNode(
                    connId,
                    connector.getType(),
                    connector.getName(),
                    programs,
                    datasets,
                    sensitive
            ));

            // Aresta: NexBridge → conector legado
            String protocol = protocolFor(connector.getType());
            edges.add(new SystemDependency(
                    "nexbridge",
                    connId,
                    protocol,
                    "NexBridge integra com " + connector.getName() + " via " + protocol
            ));
        }

        // Arestas derivadas dos mappings: conector A → conector B quando compartilham routing
        Set<String> processedPairs = new HashSet<>();
        for (MappingDefinition mapping : allMappings) {
            String connId = mapping.getConnectorId();
            // Verifica se há outros mappings que apontam para o mesmo copybook — dependência indireta
            if (mapping.getCopybookId() != null) {
                for (MappingDefinition other : allMappings) {
                    if (!other.getMappingId().equals(mapping.getMappingId())
                            && mapping.getCopybookId().equals(other.getCopybookId())
                            && !other.getConnectorId().equals(connId)) {
                        String pairKey = connId + "->" + other.getConnectorId();
                        if (processedPairs.add(pairKey)) {
                            edges.add(new SystemDependency(
                                    connId,
                                    other.getConnectorId(),
                                    "COPYBOOK_SHARED",
                                    "Compartilham copybook '" + mapping.getCopybookId() + "'"
                            ));
                        }
                    }
                }
            }
        }

        log.info("DiscoveryService: grafo construído — {} nós, {} arestas", nodes.size(), edges.size());
        return new DependencyGraph(nodes, edges, nodes.size(), edges.size(), Instant.now());
    }

    private String protocolFor(String type) {
        return switch (type) {
            case "TCP"        -> "TCP/mTLS";
            case "JDBC"       -> "JDBC/SQL";
            case "SOAP"       -> "SOAP/WSDL";
            case "IBM_MQ"     -> "IBM MQ";
            case "FILE"       -> "FTP/SFTP";
            case "SALESFORCE" -> "REST/OAuth2";
            case "SAP_RFC"    -> "SAP RFC";
            default           -> type;
        };
    }

    private List<ConnectorDefinition> resolveConnectors(String sistemaParam) {
        try {
            if ("mainframe".equalsIgnoreCase(sistemaParam)) {
                return connectorRepository.findAll().stream()
                        .filter(c -> "TCP".equals(c.getType()) || "JDBC".equals(c.getType()) || "IBM_MQ".equals(c.getType()))
                        .toList();
            } else if ("as400".equalsIgnoreCase(sistemaParam)) {
                return connectorRepository.findAll().stream()
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
                for (CopybookDefinition copybook : copybookRepository.findByConnectorId(connector.getConnectorId())) {
                    if (copybook.getParsedFields() == null) continue;
                    for (Map<String, Object> field : copybook.getParsedFields()) {
                        if (Boolean.TRUE.equals(field.get("sensitive"))) {
                            Object cobolName = field.get("cobolName");
                            Object name = field.get("name");
                            sensitive.add(name + (cobolName != null ? " (" + cobolName + ")" : ""));
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
