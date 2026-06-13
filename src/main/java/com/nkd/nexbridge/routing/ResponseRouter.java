package com.nkd.nexbridge.routing;

import com.nkd.nexbridge.domain.RoutingDefinition;
import com.nkd.nexbridge.domain.RoutingDefinitionRepository;
import com.nkd.nexbridge.governance.AlertLevel;
import com.nkd.nexbridge.governance.AlertService;
import com.nkd.nexbridge.routing.destination.RabbitMqDestination;
import com.nkd.nexbridge.routing.destination.SqsDestination;
import com.nkd.nexbridge.security.VaultService;
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
    private final PayloadFormatter payloadFormatter;
    private final AlertService alertService;
    private final VaultService vaultService;

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
                boolean conditionsMet;
                // RF-095: support OR and AND condition logic
                if ("OR".equalsIgnoreCase(destConfig.getConditionLogic())) {
                    conditionsMet = conditions.stream().anyMatch(c -> c.evaluate(responseData));
                } else {
                    // Default: AND
                    conditionsMet = conditions.stream().allMatch(c -> c.evaluate(responseData));
                }
                if (!conditionsMet) {
                    continue;
                }
            }

            // RF-096: determine format and format payload for destinations that use config-based send
            String format = destConfig.getConfig() != null
                    ? (String) destConfig.getConfig().get("format")
                    : null;

            RoutingResult result = dispatchDestination(destConfig, responseData, traceId, format);

            // RF-097: audit each routing attempt
            auditRoutingAttempt(def.getRoutingId(), destConfig, result, traceId);

            results.add(result);
        }

        return results;
    }

    private RoutingResult dispatchDestination(DestinationConfig destConfig, Map<String, Object> responseData,
                                              String traceId, String format) {
        long start = System.currentTimeMillis();
        return switch (destConfig.getType()) {
            case WEBHOOK -> webhookDestination.send(destConfig, responseData, traceId);
            case KAFKA -> kafkaDestination.send(destConfig, responseData, traceId);
            case CONNECTOR -> callerDestination.send(destConfig, responseData, traceId);
            case CRM -> crmDestination.send(destConfig, responseData, traceId);
            case RABBITMQ -> {
                try {
                    // RF-096: format payload before sending
                    String formattedPayload = payloadFormatter.format(responseData, format);
                    Map<String, Object> configWithPayload = new java.util.HashMap<>(
                            destConfig.getConfig() != null ? destConfig.getConfig() : Map.of());
                    configWithPayload.put("_formattedPayload", formattedPayload);
                    RabbitMqDestination.send(destConfig.getConfig(), responseData, vaultService);
                    long duration = System.currentTimeMillis() - start;
                    yield new RoutingResult(destConfig.getDestinationId(), destConfig.getType(),
                            true, null, null, duration);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    log.warn("RabbitMQ destination {} failed: {}", destConfig.getDestinationId(), e.getMessage());
                    yield new RoutingResult(destConfig.getDestinationId(), destConfig.getType(),
                            false, e.getMessage(), null, duration);
                }
            }
            case SQS -> {
                try {
                    // RF-096: format payload before sending
                    String formattedPayload = payloadFormatter.format(responseData, format);
                    SqsDestination.send(destConfig.getConfig(), responseData, vaultService);
                    long duration = System.currentTimeMillis() - start;
                    yield new RoutingResult(destConfig.getDestinationId(), destConfig.getType(),
                            true, null, null, duration);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    log.warn("SQS destination {} failed: {}", destConfig.getDestinationId(), e.getMessage());
                    yield new RoutingResult(destConfig.getDestinationId(), destConfig.getType(),
                            false, e.getMessage(), null, duration);
                }
            }
        };
    }

    /**
     * RF-097: Audit each routing attempt with result details.
     * Uses AlertService.sendAlert for failures (WARNING level) and INFO for success.
     * Never logs sensitive data.
     */
    private void auditRoutingAttempt(String routingRuleId, DestinationConfig destConfig,
                                     RoutingResult result, String traceId) {
        try {
            AlertLevel level = result.success() ? AlertLevel.INFO : AlertLevel.WARNING;
            String title = "Routing attempt: " + destConfig.getType() + " / " + destConfig.getDestinationId();
            String message = result.success()
                    ? "Destination reached successfully in " + result.durationMs() + "ms"
                    : "Destination failed: " + result.errorMessage();

            Map<String, String> context = new java.util.LinkedHashMap<>();
            context.put("routingRuleId", routingRuleId);
            context.put("destinationType", destConfig.getType().name());
            context.put("destinationId", destConfig.getDestinationId() != null ? destConfig.getDestinationId() : "");
            context.put("success", String.valueOf(result.success()));
            context.put("durationMs", String.valueOf(result.durationMs()));
            context.put("traceId", traceId != null ? traceId : "");
            if (!result.success() && result.errorMessage() != null) {
                context.put("errorMessage", result.errorMessage());
            }

            alertService.sendAlert(level, title, message, context);
        } catch (Exception e) {
            log.warn("Failed to audit routing attempt for destination {}: {}", destConfig.getDestinationId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<DestinationConfig> parseDestinations(List<Map<String, Object>> raw) {
        if (raw == null) return Collections.emptyList();
        List<DestinationConfig> result = new ArrayList<>();
        for (Map<String, Object> map : raw) {
            DestinationType type = DestinationType.valueOf((String) map.get("type"));
            List<RoutingCondition> conditions = parseConditions((List<Map<String, Object>>) map.get("conditions"));

            // RF-095: read conditionLogic from destination config (defaults to "AND")
            String conditionLogic = map.containsKey("conditionLogic")
                    ? (String) map.get("conditionLogic")
                    : "AND";

            DestinationConfig.DestinationConfigBuilder builder = DestinationConfig.builder()
                    .destinationId((String) map.get("destinationId"))
                    .type(type)
                    .conditions(conditions)
                    .conditionLogic(conditionLogic)
                    .connectorId((String) map.get("connectorId"))
                    .topic((String) map.get("topic"))
                    .url((String) map.get("url"))
                    .crmEndpoint((String) map.get("crmEndpoint"))
                    .headers((Map<String, String>) map.get("headers"))
                    // RF-096: parse destination-specific config (holds "format", rabbitmq/sqs params, etc.)
                    .config((Map<String, Object>) map.get("config"));

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
