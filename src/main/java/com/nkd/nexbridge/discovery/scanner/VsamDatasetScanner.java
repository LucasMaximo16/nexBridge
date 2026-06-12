package com.nkd.nexbridge.discovery.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class VsamDatasetScanner {

    public List<String> scan(String sistemaId, Map<String, Object> connectorConfig) {
        List<String> datasets = new ArrayList<>();
        try {
            if (connectorConfig == null) {
                return datasets;
            }
            Object vsamDatasets = connectorConfig.get("vsam_datasets");
            if (vsamDatasets instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        datasets.add(item.toString());
                    }
                }
            }
            Object filePath = connectorConfig.get("file_path");
            if (filePath != null) {
                datasets.add(filePath.toString());
            }
        } catch (Exception e) {
            log.warn("VsamDatasetScanner: erro ao escanear sistema {}: {}", sistemaId, e.getMessage());
        }
        log.info("VsamDatasetScanner: {} datasets encontrados", datasets.size());
        return datasets;
    }
}
