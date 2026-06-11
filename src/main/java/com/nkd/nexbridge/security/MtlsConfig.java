package com.nkd.nexbridge.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Slf4j
public class MtlsConfig {

    @Value("${nexbridge.tls.cert-path:/certs/nexbridge.crt}")
    private String certPath;

    @Value("${nexbridge.tls.key-path:/certs/nexbridge.key}")
    private String keyPath;

    @Value("${nexbridge.tls.ca-path:/certs/ca.crt}")
    private String caPath;

    @PostConstruct
    void init() {
        boolean certExists = Files.exists(Path.of(certPath));
        boolean keyExists = Files.exists(Path.of(keyPath));
        boolean caExists = Files.exists(Path.of(caPath));
        if (certExists && keyExists && caExists) {
            log.info("mTLS certificates found — connectors will use mutual TLS");
        } else {
            log.info("mTLS certificates not found at configured paths — connectors will run without mTLS (dev mode)");
        }
    }

    public String getCertPath() { return certPath; }
    public String getKeyPath() { return keyPath; }
    public String getCaPath() { return caPath; }
}
