package com.nkd.nexbridge.connector.file;

import com.nkd.nexbridge.connector.*;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.security.VaultService;
import com.jcraft.jsch.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
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
        return switch (config.getProtocol().toUpperCase()) {
            case "FTP"   -> sendFtp(request);
            case "SFTP"  -> sendSftp(request);
            case "S3"    -> sendS3(request);
            default      -> sendLocal(request);
        };
    }

    // ---- LOCAL ----
    private ConnectorResponse sendLocal(ConnectorRequest request) {
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
            log.info("FileConnector[{}] LOCAL write: {}", connectorId, filePath);
            return ConnectorResponse.builder().success(true)
                    .textPayload("File written: " + filePath)
                    .durationMs((int) (System.currentTimeMillis() - start)).build();
        } catch (IOException e) {
            throw new ConnectorException("FILE_WRITE_ERROR", e.getMessage(), e);
        }
    }

    // ---- FTP ----
    private ConnectorResponse sendFtp(ConnectorRequest request) {
        long start = System.currentTimeMillis();
        FTPClient ftp = new FTPClient();
        try {
            String password = vaultService.get(config.getPasswordRef());
            ftp.connect(config.getHost(), config.getPort());
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                throw new ConnectorException("FTP_CONNECT_ERROR",
                        "FTP server refused connection. Reply: " + reply);
            }
            if (!ftp.login(config.getUsername(), password)) {
                throw new ConnectorException("FTP_AUTH_ERROR", "FTP login failed for user: " + config.getUsername());
            }
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.enterLocalPassiveMode();

            String filename = request.getOperation() != null ? request.getOperation() : "output.dat";
            String remotePath = config.getPath() + "/" + filename;

            byte[] payload = request.getRawPayload() != null
                    ? request.getRawPayload()
                    : (request.getTextPayload() != null ? request.getTextPayload().getBytes() : new byte[0]);

            boolean stored = ftp.storeFile(remotePath, new ByteArrayInputStream(payload));
            if (!stored) {
                throw new ConnectorException("FTP_STORE_ERROR", "Failed to store file at: " + remotePath);
            }
            log.info("FileConnector[{}] FTP stored: {}", connectorId, remotePath);
            return ConnectorResponse.builder().success(true)
                    .textPayload("FTP file stored: " + remotePath)
                    .durationMs((int) (System.currentTimeMillis() - start)).build();
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("FTP_ERROR", "FTP error: " + e.getMessage(), e);
        } finally {
            try { if (ftp.isConnected()) { ftp.logout(); ftp.disconnect(); } } catch (Exception ignored) {}
        }
    }

    // ---- SFTP (JSch) ----
    private ConnectorResponse sendSftp(ConnectorRequest request) {
        long start = System.currentTimeMillis();
        JSch jsch = new JSch();
        Session sftpSession = null;
        ChannelSftp channel = null;
        try {
            String password = vaultService.get(config.getPasswordRef());
            sftpSession = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            sftpSession.setPassword(password);
            sftpSession.setConfig("StrictHostKeyChecking", "no");
            sftpSession.setTimeout(10000);
            sftpSession.connect();

            channel = (ChannelSftp) sftpSession.openChannel("sftp");
            channel.connect();

            String filename = request.getOperation() != null ? request.getOperation() : "output.dat";
            String remotePath = config.getPath() + "/" + filename;

            byte[] payload = request.getRawPayload() != null
                    ? request.getRawPayload()
                    : (request.getTextPayload() != null ? request.getTextPayload().getBytes() : new byte[0]);

            channel.put(new ByteArrayInputStream(payload), remotePath);
            log.info("FileConnector[{}] SFTP stored: {}", connectorId, remotePath);
            return ConnectorResponse.builder().success(true)
                    .textPayload("SFTP file stored: " + remotePath)
                    .durationMs((int) (System.currentTimeMillis() - start)).build();
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("SFTP_ERROR", "SFTP error: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (sftpSession != null && sftpSession.isConnected()) sftpSession.disconnect();
        }
    }

    // ---- S3 (stub — requer AWS SDK) ----
    private ConnectorResponse sendS3(ConnectorRequest request) {
        log.warn("FileConnector[{}]: S3 protocol requires AWS SDK dependency. "
                + "Add software.amazon.awssdk:s3 to pom.xml for S3 support.", connectorId);
        throw new ConnectorException("S3_NOT_CONFIGURED",
                "S3 support requires AWS SDK. Add software.amazon.awssdk:s3 to pom.xml.");
    }

    @Override
    public HealthStatus healthCheck() {
        return switch (config.getProtocol().toUpperCase()) {
            case "FTP"  -> healthFtp();
            case "SFTP" -> healthSftp();
            case "S3"   -> HealthStatus.builder().connectorId(connectorId).healthy(false)
                    .message("S3 requires AWS SDK").latencyMs(0).checkedAt(Instant.now()).build();
            default -> {
                boolean ok = Files.isReadable(Path.of(config.getPath()));
                yield HealthStatus.builder().connectorId(connectorId).healthy(ok)
                        .message(ok ? "OK" : "Path not accessible: " + config.getPath())
                        .latencyMs(0).checkedAt(Instant.now()).build();
            }
        };
    }

    private HealthStatus healthFtp() {
        long start = System.currentTimeMillis();
        FTPClient ftp = new FTPClient();
        try {
            String password = vaultService.get(config.getPasswordRef());
            ftp.connect(config.getHost(), config.getPort());
            boolean ok = ftp.login(config.getUsername(), password);
            int latency = (int) (System.currentTimeMillis() - start);
            return HealthStatus.builder().connectorId(connectorId).healthy(ok)
                    .message(ok ? "FTP connected" : "FTP login failed")
                    .latencyMs(latency).checkedAt(Instant.now()).build();
        } catch (Exception e) {
            return HealthStatus.builder().connectorId(connectorId).healthy(false)
                    .message("FTP error: " + e.getMessage()).latencyMs(0).checkedAt(Instant.now()).build();
        } finally {
            try { if (ftp.isConnected()) { ftp.logout(); ftp.disconnect(); } } catch (Exception ignored) {}
        }
    }

    private HealthStatus healthSftp() {
        long start = System.currentTimeMillis();
        JSch jsch = new JSch();
        Session sftpSession = null;
        try {
            String password = vaultService.get(config.getPasswordRef());
            sftpSession = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            sftpSession.setPassword(password);
            sftpSession.setConfig("StrictHostKeyChecking", "no");
            sftpSession.setTimeout(5000);
            sftpSession.connect();
            int latency = (int) (System.currentTimeMillis() - start);
            return HealthStatus.builder().connectorId(connectorId).healthy(true)
                    .message("SFTP connected").latencyMs(latency).checkedAt(Instant.now()).build();
        } catch (Exception e) {
            return HealthStatus.builder().connectorId(connectorId).healthy(false)
                    .message("SFTP error: " + e.getMessage()).latencyMs(0).checkedAt(Instant.now()).build();
        } finally {
            if (sftpSession != null && sftpSession.isConnected()) sftpSession.disconnect();
        }
    }

    @Override public String getConnectorId() { return connectorId; }
    @Override public ConnectorType getType() { return ConnectorType.FILE; }

    @Override
    public void shutdown() {
        if (watcher != null) watcher.stop();
        log.info("FileConnector[{}] shutdown", connectorId);
    }
}
