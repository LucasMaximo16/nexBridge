package com.nkd.nexbridge.routing;

import com.nkd.nexbridge.connector.ConnectorRegistry;
import com.nkd.nexbridge.connector.ConnectorRequest;
import com.nkd.nexbridge.mapper.FieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallerDestination {

    private final ConnectorRegistry connectorRegistry;
    private final FieldMapper fieldMapper;

    public RoutingResult send(DestinationConfig dest, Map<String, Object> payload, String traceId) {
        long start = System.currentTimeMillis();
        try {
            var connector = connectorRegistry.get(dest.getConnectorId());
            var request = ConnectorRequest.builder()
                    .params(payload)
                    .traceId(traceId)
                    .timeoutMs(5000)
                    .build();
            var response = connector.send(request);
            long duration = System.currentTimeMillis() - start;
            return new RoutingResult(dest.getDestinationId(), dest.getType(), true, null, response.getResultSet(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new RoutingResult(dest.getDestinationId(), dest.getType(), false, e.getMessage(), null, duration);
        }
    }
}
