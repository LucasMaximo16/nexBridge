package com.nkd.nexbridge.discovery.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ShadowIntegrationScanner {

    public List<String> scan(String sistemaId, Map<String, Object> connectorConfig) {
        List<String> integrations = new ArrayList<>();
        try {
            if (connectorConfig == null) {
                return integrations;
            }
            Object shadowIntegrations = connectorConfig.get("shadow_integrations");
            if (shadowIntegrations instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        integrations.add(item.toString());
                    }
                }
            }
            boolean hasFtp = connectorConfig.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase("ftp") || k.equalsIgnoreCase("sftp"));
            if (hasFtp) {
                integrations.add("FTP-TRANSFER-" + sistemaId);
            }
            boolean hasSmtp = connectorConfig.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase("smtp"));
            if (hasSmtp) {
                integrations.add("EMAIL-SMTP-" + sistemaId);
            }
        } catch (Exception e) {
            log.warn("ShadowIntegrationScanner: erro ao escanear sistema {}: {}", sistemaId, e.getMessage());
        }
        log.info("ShadowIntegrationScanner: {} integrações shadow detectadas", integrations.size());
        return integrations;
    }
}
