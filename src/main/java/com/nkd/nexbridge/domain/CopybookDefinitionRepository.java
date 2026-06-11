package com.nkd.nexbridge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CopybookDefinitionRepository extends JpaRepository<CopybookDefinition, UUID> {
    Optional<CopybookDefinition> findByCopybookIdAndVersion(String copybookId, String version);
    List<CopybookDefinition> findByCopybookId(String copybookId);
    List<CopybookDefinition> findByConnectorId(String connectorId);
}
