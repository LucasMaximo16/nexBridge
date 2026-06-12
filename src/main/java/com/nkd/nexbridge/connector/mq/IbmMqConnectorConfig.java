package com.nkd.nexbridge.connector.mq;

import lombok.Builder;
import lombok.Data;

@Builder @Data
public class IbmMqConnectorConfig {
    private String host;
    private int port;
    private String channel;
    private String queueManager;
    private String username;
    private String passwordRef;
    private String requestQueue;
    private String responseQueue;
    private int timeoutMs;
}
