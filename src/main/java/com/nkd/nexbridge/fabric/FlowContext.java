package com.nkd.nexbridge.fabric;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@Data
public class FlowContext {
    private String traceId;
    private String consumerId;
    private String consumerIp;
    private String endpoint;
    private String method;
    private Map<String, Object> requestData;
    private Map<String, Object> responseData;
    private String connectorId;
    private String mappingId;
    private String mappingVersion;
    private String copybookId;
    private String sourceSystem;
    private List<String> discardedFields;
    private List<String> maskedFields;
    private Instant startedAt;
    private int durationMs;
    private boolean success;
    private String errorCode;
    private String errorMessage;
}
