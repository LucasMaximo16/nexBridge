package com.nkd.nexbridge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorDefinitionRepository extends JpaRepository<ConnectorDefinition, UUID> {
    Optional<ConnectorDefinition> findByConnectorId(String connectorId);
    List<ConnectorDefinition> findByEnabled(boolean enabled);
    List<ConnectorDefinition> findByType(String type);
}
