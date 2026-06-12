package com.nkd.nexbridge.connector.jdbc;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class JdbcConnectorConfig {
    private String jdbcUrl;
    private String driverClass;
    private String username;
    private String passwordRef;
    private int poolMin;
    private int poolMax;
}
