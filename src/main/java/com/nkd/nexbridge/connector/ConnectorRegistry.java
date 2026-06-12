package com.nkd.nexbridge.connector;

import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.domain.ConnectorDefinitionRepository;
import com.nkd.nexbridge.exception.ConnectorException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectorRegistry {

    private final ConnectorDefinitionRepository connectorRepository;
    private final ConnectorFactory connectorFactory;

    private final ConcurrentHashMap<String, LegacyConnector> registry = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        connectorRepository.findByEnabled(true).forEach(def -> {
            try {
                register(def);
            } catch (Exception e) {
                log.warn("Failed to initialize connector {}: {}", def.getConnectorId(), e.getMessage());
            }
        });
        log.info("ConnectorRegistry initialized with {} connectors", registry.size());
    }

    public void register(ConnectorDefinition def) {
        LegacyConnector connector = connectorFactory.create(def);
        connector.initialize(def);
        registry.put(def.getConnectorId(), connector);
        log.info("Connector registered: {} ({})", def.getConnectorId(), def.getType());
    }

    public LegacyConnector get(String connectorId) {
        LegacyConnector connector = registry.get(connectorId);
        if (connector == null) {
            throw new ConnectorException("CONNECTOR_NOT_FOUND",
                    "Conector não encontrado ou não inicializado: " + connectorId);
        }
        return connector;
    }

    public Optional<LegacyConnector> find(String connectorId) {
        return Optional.ofNullable(registry.get(connectorId));
    }

    public Collection<LegacyConnector> findAll() {
        return registry.values();
    }

    public void unregister(String connectorId) {
        LegacyConnector connector = registry.remove(connectorId);
        if (connector != null) {
            connector.shutdown();
        }
    }
}
