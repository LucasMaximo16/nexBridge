package com.nkd.nexbridge.connector.tcp;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TcpConnectorConfig {
    private String host;
    private int port;
    private boolean tlsEnabled;
    private String tlsVersion;
    private String certPath;
    private String keyPath;
    private String caPath;
    private boolean verifyPeer;
    private int poolMin;
    private int poolMax;
    private int timeoutMs;
    private String encoding;
    private String copybookId;
    private int headerBytes;
}
