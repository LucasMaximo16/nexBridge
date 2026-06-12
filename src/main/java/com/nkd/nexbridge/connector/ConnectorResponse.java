package com.nkd.nexbridge.connector;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Builder
@Data
public class ConnectorResponse {
    private boolean success;
    private byte[] rawPayload;
    private String textPayload;
    private Map<String, Object> resultSet;
    private int durationMs;
    private String errorCode;
    private String errorMessage;
}
