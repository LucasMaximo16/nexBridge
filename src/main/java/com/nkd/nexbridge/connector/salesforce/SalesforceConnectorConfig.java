package com.nkd.nexbridge.connector.salesforce;

import lombok.Data;

@Data
public class SalesforceConnectorConfig {
    private String clientIdRef;      // vault://sf/client_id
    private String clientSecretRef;  // vault://sf/client_secret
    private String tokenUrl;         // https://login.salesforce.com/services/oauth2/token
    private String instanceUrl;      // resolvido após autenticação
    private String authType;         // OAUTH2_CLIENT_CREDENTIALS
    private String direction;        // BIDIRECTIONAL
    private boolean pushOnEvent;
    private int pullIntervalSec;
}
