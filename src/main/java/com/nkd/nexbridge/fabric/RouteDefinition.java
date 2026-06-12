package com.nkd.nexbridge.fabric;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RouteDefinition {
    private String path;
    private String method;
    private String version;
    private String connectorId;
    private String mappingId;
    private String mappingVersion;
    private String authMethod;
    private int rateLimitPerMinute;
    private int cacheTtlSec;
    private String status;
}
