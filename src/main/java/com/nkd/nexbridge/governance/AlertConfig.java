package com.nkd.nexbridge.governance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "nexbridge.alerts")
@Data
public class AlertConfig {

    private boolean enabled;
    private String slackWebhookUrl;
    private String slackChannel = "#nexbridge-alerts";
    private boolean emailEnabled;
    private List<String> emailTo = List.of();
    private String smtpHost = "localhost";
    private int smtpPort = 587;
    private String smtpUser;
    /** vault:// reference — never plain text */
    private String smtpPasswordRef;
    private boolean pagerdutyEnabled;
    private String pagerdutyRoutingKey;
}
