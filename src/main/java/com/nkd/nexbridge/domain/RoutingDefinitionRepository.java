package com.nkd.nexbridge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoutingDefinitionRepository extends JpaRepository<RoutingDefinition, UUID> {
    Optional<RoutingDefinition> findByRoutingId(String routingId);
    Optional<RoutingDefinition> findByApiPathAndApiMethod(String apiPath, String apiMethod);
    List<RoutingDefinition> findByActive(boolean active);
}
