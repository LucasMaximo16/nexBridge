package com.nkd.nexbridge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MappingDefinitionRepository extends JpaRepository<MappingDefinition, UUID> {
    Optional<MappingDefinition> findByMappingIdAndVersion(String mappingId, String version);
    List<MappingDefinition> findByConnectorId(String connectorId);
    List<MappingDefinition> findByActive(boolean active);
}
