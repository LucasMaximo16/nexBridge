package com.nkd.nexbridge.connector.file;

import lombok.Builder;
import lombok.Data;

@Builder @Data
public class FileConnectorConfig {
    private String protocol;
    private String host;
    private int port;
    private String username;
    private String passwordRef;
    private String path;
    private String filePattern;
    private String encoding;
    private String copybookId;
    private int pollIntervalSec;
    private boolean moveAfterRead;
    private String archivePath;
}
