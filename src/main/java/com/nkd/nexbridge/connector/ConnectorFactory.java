package com.nkd.nexbridge.connector;

import com.nkd.nexbridge.connector.file.FileConnector;
import com.nkd.nexbridge.connector.jdbc.JdbcConnector;
import com.nkd.nexbridge.connector.mq.IbmMqConnector;
import com.nkd.nexbridge.connector.sap.SapRfcConnector;
import com.nkd.nexbridge.connector.salesforce.SalesforceConnector;
import com.nkd.nexbridge.connector.soap.SoapConnector;
import com.nkd.nexbridge.connector.tcp.TcpConnector;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.security.MtlsConfig;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConnectorFactory {

    private final VaultService vaultService;
    private final MtlsConfig mtlsConfig;

    public LegacyConnector create(ConnectorDefinition def) {
        ConnectorType type = ConnectorType.valueOf(def.getType());
        return switch (type) {
            case TCP        -> new TcpConnector(vaultService, mtlsConfig);
            case JDBC       -> new JdbcConnector(vaultService);
            case SOAP       -> new SoapConnector(vaultService);
            case IBM_MQ     -> new IbmMqConnector(vaultService);
            case FILE       -> new FileConnector(vaultService);
            case SALESFORCE -> new SalesforceConnector(vaultService);
            case SAP_RFC    -> new SapRfcConnector(vaultService);
        };
    }
}
