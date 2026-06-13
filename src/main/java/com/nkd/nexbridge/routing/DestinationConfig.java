package com.nkd.nexbridge.routing;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DestinationConfig {
    private String destinationId;
    private DestinationType type;
    private List<RoutingCondition> conditions;
    /** "AND" (default) or "OR" — controls how conditions are evaluated. */
    @Builder.Default
    private String conditionLogic = "AND";
    private String connectorId;
    private String topic;
    private String url;
    private String crmEndpoint;
    @Builder.Default
    private int retryCount = 3;
    @Builder.Default
    private long retryDelayMs = 1000;
    private Map<String, String> headers;
    /** Destination-specific config (e.g. for RABBITMQ, SQS). Also holds "format" key. */
    private Map<String, Object> config;
}
