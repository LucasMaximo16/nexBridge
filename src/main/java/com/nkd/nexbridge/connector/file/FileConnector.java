package com.nkd.nexbridge.connector.file;

import com.nkd.nexbridge.connector.*;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class FileConnector implements LegacyConnector {

    private final VaultService vaultService;
    private FileConnectorConfig config;
    private String connectorId;
    private FileWatcher watcher;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) cfg.getOrDefault("connection", cfg);
        this.config = FileConnectorConfig.builder()
                .protocol((String) conn.getOrDefault("protocol", "LOCAL"))
                .host((String) conn.get("host"))
                .port(((Number) conn.getOrDefault("port", 22)).intValue())
                .username((String) conn.get("username"))
                .passwordRef((String) conn.get("password_ref"))
                .path((String) conn.getOrDefault("path", "/tmp"))
                .filePattern((String) conn.getOrDefault("file_pattern", "*.DAT"))
                .encoding((String) conn.getOrDefault("encoding", "UTF-8"))
                .pollIntervalSec(((Number) conn.getOrDefault("poll_interval_sec", 30)).intValue())
                .moveAfterRead(Boolean.TRUE.equals(conn.get("move_after_read")))
                .archivePath((String) conn.get("archive_path"))
                .build();

        if ("LOCAL".equalsIgnoreCase(config.getProtocol())) {
            this.watcher = new FileWatcher(config,
                    path -> log.info("FileConnector[{}] file found: {}", connectorId, path));
            this.watcher.start();
        }
        log.info("FileConnector initialized: {} protocol={}", connectorId, config.getProtocol());
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        long start = System.currentTimeMillis();
        try {
            String filename = request.getOperation() != null ? request.getOperation() : "output.dat";
            Path filePath = Path.of(config.getPath(), filename);
            Files.createDirectories(filePath.getParent());
            if (request.getRawPayload() != null) {
                Files.write(filePath, request.getRawPayload());
            } else if (request.getTextPayload() != null) {
                Files.writeString(filePath, request.getTextPayload());
            }
            return ConnectorResponse.builder().success(true)
                    .textPayload("File written: " + filePath)
                    .durationMs((int) (System.currentTimeMillis() - start)).build();
        } catch (IOException e) {
            throw new ConnectorException("FILE_WRITE_ERROR", e.getMessage(), e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        boolean ok = Files.isReadable(Path.of(config.getPath()));
        return HealthStatus.builder().connectorId(connectorId).healthy(ok)
                .message(ok ? "OK" : "Path not accessible: " + config.getPath())
                .latencyMs(0).checkedAt(Instant.now()).build();
    }

    @Override public String getConnectorId() { return connectorId; }
    @Override public ConnectorType getType() { return ConnectorType.FILE; }
    @Override public void shutdown() {
        if (watcher != null) watcher.stop();
        log.info("FileConnector[{}] shutdown", connectorId);
    }
}
