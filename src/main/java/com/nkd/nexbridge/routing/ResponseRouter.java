package com.nkd.nexbridge.routing;

import com.nkd.nexbridge.domain.RoutingDefinition;
import com.nkd.nexbridge.domain.RoutingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseRouter {

    private final WebhookDestination webhookDestination;
    private final KafkaDestination kafkaDestination;
    private final CallerDestination callerDestination;
    private final CrmDestination crmDestination;
    private final RoutingDefinitionRepository routingDefinitionRepository;

    public List<RoutingResult> route(String apiPath, String apiMethod, Map<String, Object> responseData, String traceId) {
        var definition = routingDefinitionRepository.findByApiPathAndApiMethod(apiPath, apiMethod);
        if (definition.isEmpty()) {
            return Collections.emptyList();
        }

        RoutingDefinition def = definition.get();
        if (!def.isActive()) {
            return Collections.emptyList();
        }

        List<DestinationConfig> destinations = parseDestinations(def.getDestinations());
        List<RoutingResult> results = new ArrayList<>();

        for (DestinationConfig destConfig : destinations) {
            List<RoutingCondition> conditions = destConfig.getConditions();
            if (conditions != null && !conditions.isEmpty()) {
                boolean allMatch = conditions.stream().allMatch(c -> c.evaluate(responseData));
                if (!allMatch) {
                    continue;
                }
            }

            RoutingResult result = switch (destConfig.getType()) {
                case WEBHOOK -> webhookDestination.send(destConfig, responseData, traceId);
                case KAFKA -> kafkaDestination.send(destConfig, responseData, traceId);
                case CONNECTOR -> callerDestination.send(destConfig, responseData, traceId);
                case CRM -> crmDestination.send(destConfig, responseData, traceId);
            };
            results.add(result);
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private List<DestinationConfig> parseDestinations(List<Map<String, Object>> raw) {
        if (raw == null) return Collections.emptyList();
        List<DestinationConfig> result = new ArrayList<>();
        for (Map<String, Object> map : raw) {
            DestinationType type = DestinationType.valueOf((String) map.get("type"));
            List<RoutingCondition> conditions = parseConditions((List<Map<String, Object>>) map.get("conditions"));

            DestinationConfig.DestinationConfigBuilder builder = DestinationConfig.builder()
                    .destinationId((String) map.get("destinationId"))
                    .type(type)
                    .conditions(conditions)
                    .connectorId((String) map.get("connectorId"))
                    .topic((String) map.get("topic"))
                    .url((String) map.get("url"))
                    .crmEndpoint((String) map.get("crmEndpoint"))
                    .headers((Map<String, String>) map.get("headers"));

            if (map.containsKey("retryCount")) {
                builder.retryCount(((Number) map.get("retryCount")).intValue());
            }
            if (map.containsKey("retryDelayMs")) {
                builder.retryDelayMs(((Number) map.get("retryDelayMs")).longValue());
            }

            result.add(builder.build());
        }
        return result;
    }

    private List<RoutingCondition> parseConditions(List<Map<String, Object>> rawConditions) {
        if (rawConditions == null) return Collections.emptyList();
        List<RoutingCondition> conditions = new ArrayList<>();
        for (Map<String, Object> map : rawConditions) {
            conditions.add(new RoutingCondition(
                    (String) map.get("field"),
                    ConditionOperator.valueOf((String) map.get("operator")),
                    (String) map.get("value")
            ));
        }
        return conditions;
    }
}
