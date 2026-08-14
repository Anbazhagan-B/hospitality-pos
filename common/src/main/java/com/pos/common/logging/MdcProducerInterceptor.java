package com.pos.common.logging;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Stamps the current correlation id onto every outbound Kafka record.
 *
 * <p>Instantiated by the Kafka client itself, not by Spring, so it must have a
 * public no-arg constructor and must not depend on injected beans. Enable it
 * per service with:
 *
 * <pre>
 * spring:
 *   kafka:
 *     producer:
 *       properties:
 *         interceptor.classes: com.pos.common.logging.MdcProducerInterceptor
 * </pre>
 *
 * <p>Reading straight from {@link MDC} works because {@code KafkaTemplate.send}
 * is called on the caller's thread — the same request thread the servlet filter
 * populated. The asynchronous part is the broker acknowledgement, which happens
 * after the header is already attached.
 */
public class MdcProducerInterceptor implements ProducerInterceptor<Object, Object> {

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        String correlationId = MDC.get(LoggingConstants.CORRELATION_ID);
        if (correlationId != null && !correlationId.isEmpty()
                && record.headers().lastHeader(LoggingConstants.CORRELATION_ID_KAFKA_HEADER) == null) {
            record.headers().add(LoggingConstants.CORRELATION_ID_KAFKA_HEADER,
                    correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // No-op. Delivery outcome is already logged by OrderEventPublisher.
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration required.
    }
}
