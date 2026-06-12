package com.nkd.nexbridge.connector;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Builder
@Data
public class ConnectorRequest {
    private String connectorId;
    private String traceId;
    private byte[] rawPayload;
    private String textPayload;
    private Map<String, Object> params;
    private String operation;
    private int timeoutMs;
}
