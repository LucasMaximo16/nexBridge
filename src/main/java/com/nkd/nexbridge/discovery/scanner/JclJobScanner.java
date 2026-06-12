package com.nkd.nexbridge.discovery.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class JclJobScanner {

    public record JclJob(String name, String description, String lastReturnCode, String averageDuration) {}

    @SuppressWarnings("unchecked")
    public List<JclJob> scan(String sistemaId, Map<String, Object> connectorConfig) {
        List<JclJob> jobs = new ArrayList<>();
        try {
            if (connectorConfig == null) {
                return jobs;
            }
            Object jclJobs = connectorConfig.get("jcl_jobs");
            if (jclJobs instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> jobMap) {
                        Map<String, Object> job = (Map<String, Object>) jobMap;
                        String name = getString(job, "name");
                        String description = getString(job, "description");
                        String lastRc = getString(job, "last_rc");
                        String avgDuration = getString(job, "avg_duration");
                        jobs.add(new JclJob(name, description, lastRc, avgDuration));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JclJobScanner: erro ao escanear sistema {}: {}", sistemaId, e.getMessage());
        }
        log.info("JclJobScanner: {} jobs encontrados", jobs.size());
        return jobs;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
