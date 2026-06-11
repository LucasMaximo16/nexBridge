package com.nkd.nexbridge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, UUID> {
    Optional<ApiDefinition> findByPathAndMethodAndVersion(String path, String method, String version);
    List<ApiDefinition> findByStatus(String status);
    List<ApiDefinition> findByConnectorId(String connectorId);
}
