package com.nkd.nexbridge.connector.totvs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TotvsProtheusConnectorConfig {
    private String baseUrl;           // http://10.0.0.50:8080/rest
    private String username;
    private String passwordRef;       // vault://totvs/password
    private String authType;          // BASIC | BEARER | NONE
    private String tokenRef;          // vault://totvs/token (se BEARER)
    private String company;           // código da empresa (filial)
    private String branch;            // código da filial
    private int timeoutMs;
    private int maxRetries;
}
