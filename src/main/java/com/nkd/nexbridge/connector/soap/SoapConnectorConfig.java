package com.nkd.nexbridge.connector.soap;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SoapConnectorConfig {
    private String wsdlUrl;
    private String authType;
    private String username;
    private String passwordRef;
    private int timeoutMs;
}
