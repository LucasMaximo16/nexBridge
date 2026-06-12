package com.nkd.nexbridge.routing;

import java.util.Map;

public record RoutingResult(
        String destinationId,
        DestinationType type,
        boolean success,
        String errorMessage,
        Map<String, Object> responseData,
        long durationMs
) {}
