package com.nkd.nexbridge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerDefinitionRepository extends JpaRepository<ConsumerDefinition, UUID> {
    Optional<ConsumerDefinition> findByConsumerId(String consumerId);
    Optional<ConsumerDefinition> findByConsumerIdAndActive(String consumerId, boolean active);
}
