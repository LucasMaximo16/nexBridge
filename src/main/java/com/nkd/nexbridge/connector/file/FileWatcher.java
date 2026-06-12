package com.nkd.nexbridge.connector.file;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class FileWatcher {

    private final FileConnectorConfig config;
    private final Consumer<Path> onFileFound;
    private ScheduledExecutorService scheduler;

    public FileWatcher(FileConnectorConfig config, Consumer<Path> onFileFound) {
        this.config = config;
        this.onFileFound = onFileFound;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "file-watcher-" + config.getPath());
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::poll, 0, config.getPollIntervalSec(), TimeUnit.SECONDS);
        log.info("FileWatcher started: {} pattern={}", config.getPath(), config.getFilePattern());
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void poll() {
        try {
            Path dir = Path.of(config.getPath());
            if (!Files.exists(dir)) return;
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + config.getFilePattern());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    if (matcher.matches(entry.getFileName())) {
                        onFileFound.accept(entry);
                        if (config.isMoveAfterRead() && config.getArchivePath() != null) {
                            Path archive = Path.of(config.getArchivePath(), entry.getFileName().toString());
                            Files.createDirectories(archive.getParent());
                            Files.move(entry, archive, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("FileWatcher poll error: {}", e.getMessage());
        }
    }
}
