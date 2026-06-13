package com.nkd.nexbridge.connector.mq;

import com.nkd.nexbridge.connector.*;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.security.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.jms.*;
import java.time.Instant;
import java.util.Map;

/**
 * IBM MQ connector via JMS API (jakarta.jms).
 * Uses reflection to load IBM MQ JMS provider at runtime — falls back gracefully
 * if the IBM MQ client jar is not on the classpath (e.g., in dev/test environments).
 * In production the jar should be placed in lib/ and added to the classpath.
 */
@Slf4j
@RequiredArgsConstructor
public class IbmMqConnector implements LegacyConnector {

    private static final String MQ_FACTORY_CLASS = "com.ibm.mq.jms.MQConnectionFactory";

    private final VaultService vaultService;
    private IbmMqConnectorConfig config;
    private String connectorId;

    private ConnectionFactory connectionFactory;
    private boolean mqAvailable = false;

    @Override
    public void initialize(ConnectorDefinition def) {
        this.connectorId = def.getConnectorId();
        Map<String, Object> cfg = def.getConfig();

        @SuppressWarnings("unchecked")
        Map<String, Object> conn = (Map<String, Object>) cfg.getOrDefault("connection", cfg);
        @SuppressWarnings("unchecked")
        Map<String, Object> queues = (Map<String, Object>) cfg.getOrDefault("queues", Map.of());

        this.config = IbmMqConnectorConfig.builder()
                .host((String) conn.getOrDefault("host", "localhost"))
                .port(((Number) conn.getOrDefault("port", 1414)).intValue())
                .channel((String) conn.getOrDefault("channel", "NXB.SVRCONN"))
                .queueManager((String) conn.getOrDefault("queue_manager", "QM1"))
                .username((String) conn.get("username"))
                .passwordRef((String) conn.get("password_ref"))
                .requestQueue((String) queues.getOrDefault("request", "NXB.IN.Q"))
                .responseQueue((String) queues.getOrDefault("response", "NXB.OUT.Q"))
                .timeoutMs(((Number) queues.getOrDefault("timeout_ms", 5000)).intValue())
                .build();

        this.connectionFactory = tryBuildConnectionFactory();
        log.info("IbmMqConnector initialized: {} mqAvailable={}", connectorId, mqAvailable);
    }

    @Override
    public ConnectorResponse send(ConnectorRequest request) {
        if (!mqAvailable || connectionFactory == null) {
            log.warn("IbmMqConnector[{}]: IBM MQ jar not on classpath — cannot send message", connectorId);
            throw new ConnectorException("MQ_NOT_AVAILABLE",
                    "IBM MQ client library not available. Add ibm-mq-allclient.jar to classpath.");
        }

        long start = System.currentTimeMillis();
        Connection connection = null;
        try {
            String password = config.getPasswordRef() != null
                    ? vaultService.get(config.getPasswordRef())
                    : null;

            connection = password != null
                    ? connectionFactory.createConnection(config.getUsername(), password)
                    : connectionFactory.createConnection();
            connection.start();

            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Queue requestQueue = session.createQueue(config.getRequestQueue());
                Queue responseQueue = session.createQueue(config.getResponseQueue());

                MessageProducer producer = session.createProducer(requestQueue);
                producer.setTimeToLive(config.getTimeoutMs());

                // Build message
                Message message;
                if (request.getRawPayload() != null) {
                    BytesMessage bytes = session.createBytesMessage();
                    bytes.writeBytes(request.getRawPayload());
                    message = bytes;
                } else {
                    TextMessage text = session.createTextMessage(
                            request.getTextPayload() != null ? request.getTextPayload() : "");
                    message = text;
                }
                message.setStringProperty("NXB_TRACE_ID", request.getTraceId());
                if (request.getOperation() != null) {
                    message.setStringProperty("NXB_OPERATION", request.getOperation());
                }

                // Correlation for request-reply
                String correlationId = request.getTraceId();
                message.setJMSCorrelationID(correlationId);
                producer.send(message);
                log.info("IbmMqConnector[{}]: message sent to {}, correlationId={}", connectorId, config.getRequestQueue(), correlationId);

                // Wait for reply
                String selector = "JMSCorrelationID = '" + correlationId + "'";
                MessageConsumer consumer = session.createConsumer(responseQueue, selector);
                Message reply = consumer.receive(config.getTimeoutMs());

                int duration = (int) (System.currentTimeMillis() - start);
                if (reply == null) {
                    throw new ConnectorException("MQ_TIMEOUT",
                            "No reply from IBM MQ after " + config.getTimeoutMs() + "ms");
                }

                String replyText = null;
                byte[] replyBytes = null;
                if (reply instanceof TextMessage tm) {
                    replyText = tm.getText();
                } else if (reply instanceof BytesMessage bm) {
                    replyBytes = new byte[(int) bm.getBodyLength()];
                    bm.readBytes(replyBytes);
                }

                log.info("IbmMqConnector[{}]: reply received, duration={}ms", connectorId, duration);
                return ConnectorResponse.builder()
                        .success(true)
                        .textPayload(replyText)
                        .rawPayload(replyBytes)
                        .durationMs(duration)
                        .build();
            }
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("MQ_ERROR", "IBM MQ error: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try { connection.close(); } catch (JMSException ignored) {}
            }
        }
    }

    @Override
    public HealthStatus healthCheck() {
        if (!mqAvailable || connectionFactory == null) {
            return HealthStatus.builder().connectorId(connectorId).healthy(false)
                    .message("IBM MQ client jar not on classpath")
                    .latencyMs(0).checkedAt(Instant.now()).build();
        }
        long start = System.currentTimeMillis();
        Connection connection = null;
        try {
            String password = config.getPasswordRef() != null
                    ? vaultService.get(config.getPasswordRef()) : null;
            connection = password != null
                    ? connectionFactory.createConnection(config.getUsername(), password)
                    : connectionFactory.createConnection();
            connection.start();
            int latency = (int) (System.currentTimeMillis() - start);
            return HealthStatus.builder().connectorId(connectorId).healthy(true)
                    .message("Connected to QM " + config.getQueueManager())
                    .latencyMs(latency).checkedAt(Instant.now()).build();
        } catch (Exception e) {
            return HealthStatus.builder().connectorId(connectorId).healthy(false)
                    .message("Connection failed: " + e.getMessage())
                    .latencyMs(0).checkedAt(Instant.now()).build();
        } finally {
            if (connection != null) {
                try { connection.close(); } catch (JMSException ignored) {}
            }
        }
    }

    @Override public String getConnectorId() { return connectorId; }
    @Override public ConnectorType getType() { return ConnectorType.IBM_MQ; }
    @Override public void shutdown() { log.info("IbmMqConnector[{}] shutdown", connectorId); }

    private ConnectionFactory tryBuildConnectionFactory() {
        try {
            Class<?> factoryClass = Class.forName(MQ_FACTORY_CLASS);
            Object factory = factoryClass.getDeclaredConstructor().newInstance();
            factoryClass.getMethod("setHostName", String.class).invoke(factory, config.getHost());
            factoryClass.getMethod("setPort", int.class).invoke(factory, config.getPort());
            factoryClass.getMethod("setChannel", String.class).invoke(factory, config.getChannel());
            factoryClass.getMethod("setQueueManager", String.class).invoke(factory, config.getQueueManager());
            // Transport type 1 = CLIENT
            factoryClass.getMethod("setTransportType", int.class).invoke(factory, 1);
            this.mqAvailable = true;
            return (ConnectionFactory) factory;
        } catch (ClassNotFoundException e) {
            log.warn("IbmMqConnector[{}]: IBM MQ client jar not found on classpath ({}). "
                    + "Add ibm-mq-allclient.jar to run IBM MQ integration.", connectorId, MQ_FACTORY_CLASS);
            return null;
        } catch (Exception e) {
            log.warn("IbmMqConnector[{}]: Failed to configure IBM MQ factory: {}", connectorId, e.getMessage());
            return null;
        }
    }
}
