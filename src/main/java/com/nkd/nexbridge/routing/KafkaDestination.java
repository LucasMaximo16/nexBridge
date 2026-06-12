package com.nkd.nexbridge.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaDestination {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RoutingResult send(DestinationConfig dest, Map<String, Object> payload, String traceId) {
        long start = System.currentTimeMillis();
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(dest.getTopic(), traceId, payload);
            record.headers().add(new RecordHeader("X-Trace-Id", traceId.getBytes(StandardCharsets.UTF_8)));

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Kafka send failed for destination {}: {}", dest.getDestinationId(), ex.getMessage());
                } else {
                    log.info("Kafka message sent to topic {} partition {}", result.getRecordMetadata().topic(), result.getRecordMetadata().partition());
                }
            });

            long duration = System.currentTimeMillis() - start;
            return new RoutingResult(dest.getDestinationId(), dest.getType(), true, null, null, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new RoutingResult(dest.getDestinationId(), dest.getType(), false, e.getMessage(), null, duration);
        }
    }
}
