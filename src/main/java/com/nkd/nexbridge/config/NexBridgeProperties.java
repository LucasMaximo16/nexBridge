package com.nkd.nexbridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nexbridge")
@Data
public class NexBridgeProperties {
    private String version = "1.0.0";
}
