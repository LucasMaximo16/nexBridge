package com.nkd.nexbridge.connector.sap;

import lombok.Data;

@Data
public class SapRfcConnectorConfig {
    private String host;
    private String systemNumber;  // "00"
    private String client;        // "100"
    private String username;
    private String passwordRef;   // vault://sap/rfc-password
    private String language;      // "PT"
}
