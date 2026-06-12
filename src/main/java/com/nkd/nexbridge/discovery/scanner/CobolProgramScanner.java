package com.nkd.nexbridge.discovery.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CobolProgramScanner {

    @SuppressWarnings("unchecked")
    public List<String> scan(String sistemaId, Map<String, Object> connectorConfig) {
        List<String> programs = new ArrayList<>();
        try {
            if (connectorConfig == null) {
                return programs;
            }
            Object cobolPrograms = connectorConfig.get("cobol_programs");
            if (cobolPrograms instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        programs.add(item.toString());
                    }
                }
            }
            if (programs.isEmpty()) {
                Object copybook = connectorConfig.get("copybook");
                if (copybook != null) {
                    String copybookName = copybook.toString();
                    String programName = copybookName.replaceAll("\\.[^.]+$", "").toUpperCase();
                    programs.add(programName);
                }
            }
        } catch (Exception e) {
            log.warn("CobolProgramScanner: erro ao escanear sistema {}: {}", sistemaId, e.getMessage());
        }
        log.info("CobolProgramScanner: {} programas encontrados para sistema {}", programs.size(), sistemaId);
        return programs;
    }
}
