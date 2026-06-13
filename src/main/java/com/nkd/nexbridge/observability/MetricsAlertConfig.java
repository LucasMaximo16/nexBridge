package com.nkd.nexbridge.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "nexbridge.observability.alerts")
@Data
public class MetricsAlertConfig {

    private double errorRateThreshold = 0.1;
    private double latencyP99ThresholdMs = 2000.0;
    private long checkIntervalSeconds = 60;
}
