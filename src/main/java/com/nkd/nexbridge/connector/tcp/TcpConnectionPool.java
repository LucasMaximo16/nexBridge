package com.nkd.nexbridge.connector.tcp;

import com.nkd.nexbridge.exception.ConnectorException;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class TcpConnectionPool {

    private final TcpConnectorConfig config;
    private final BlockingQueue<Socket> pool;

    public TcpConnectionPool(TcpConnectorConfig config) {
        this.config = config;
        this.pool = new ArrayBlockingQueue<>(config.getPoolMax());
    }

    public Socket acquire() {
        Socket socket = pool.poll();
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            return socket;
        }
        return createSocket();
    }

    public void release(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            if (!pool.offer(socket)) {
                closeQuietly(socket);
            }
        }
    }

    public void shutdown() {
        pool.forEach(this::closeQuietly);
        pool.clear();
    }

    private Socket createSocket() {
        try {
            if (config.isTlsEnabled()) {
                return createTlsSocket();
            }
            Socket socket = new Socket(config.getHost(), config.getPort());
            socket.setSoTimeout(config.getTimeoutMs());
            return socket;
        } catch (Exception e) {
            throw new ConnectorException("TCP_CONNECTION_FAILED",
                    "Falha ao conectar em " + config.getHost() + ":" + config.getPort() + " — " + e.getMessage());
        }
    }

    private Socket createTlsSocket() throws Exception {
        SSLContext sslContext = buildSslContext();
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket(config.getHost(), config.getPort());
        socket.setEnabledProtocols(new String[]{config.getTlsVersion() != null ? config.getTlsVersion() : "TLSv1.3"});
        socket.setSoTimeout(config.getTimeoutMs());
        socket.startHandshake();
        return socket;
    }

    private SSLContext buildSslContext() throws Exception {
        // Load client certificate (mTLS)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        if (config.getCertPath() != null && !config.getCertPath().isBlank()) {
            try (FileInputStream fis = new FileInputStream(config.getCertPath())) {
                keyStore.load(fis, null);
            }
        } else {
            keyStore.load(null, null);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        // Load CA trust store
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        if (config.getCaPath() != null && !config.getCaPath().isBlank()) {
            try (FileInputStream fis = new FileInputStream(config.getCaPath())) {
                trustStore.load(fis, null);
            }
        } else {
            trustStore.load(null, null);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    private void closeQuietly(Socket socket) {
        try { socket.close(); } catch (Exception ignored) {}
    }
}
