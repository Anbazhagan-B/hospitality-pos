package com.pos.common.logging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Carries the correlation id across the Kafka hop.
 *
 * <p>An HTTP correlation id dies at the producer: check-service publishes an
 * {@code OrderEvent} and returns, and kitchen-display-service picks it up later
 * on a completely different thread in a different pod. Without propagation, the
 * kitchen side of an order is unlinkable to the terminal side — which is
 * exactly the part of the flow you most want to trace.
 *
 * <p>The producer half is {@link MdcProducerInterceptor}, wired through
 * {@code spring.kafka.producer.properties.interceptor.classes}. This class
 * supplies the consumer half.
 *
 * <p>Registering a single {@link RecordInterceptor} bean is enough: Spring
 * Boot's Kafka auto-configuration injects it into the auto-configured
 * {@code ConcurrentKafkaListenerContainerFactory}, so no listener code changes.
 * {@code @ConditionalOnClass} keeps the whole configuration inert in the four
 * services that don't depend on spring-kafka.
 */
@Configuration
@ConditionalOnClass(RecordInterceptor.class)
public class KafkaMdcConfig {

    @Bean
    public RecordInterceptor<Object, Object> mdcRecordInterceptor() {
        return new RecordInterceptor<>() {

            @Override
            public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                            Consumer<Object, Object> consumer) {
                MDC.put(LoggingConstants.CORRELATION_ID, correlationIdFrom(record));
                MDC.put(LoggingConstants.KAFKA_TOPIC, record.topic());
                return record;
            }

            @Override
            public void afterRecord(ConsumerRecord<Object, Object> record,
                                    Consumer<Object, Object> consumer) {
                // Listener container threads are long-lived and process many
                // records, so the context must be cleared between them.
                MDC.clear();
            }
        };
    }

    private static String correlationIdFrom(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(LoggingConstants.CORRELATION_ID_KAFKA_HEADER);
        if (header != null && header.value() != null && header.value().length > 0) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        // An event published before this change, or by an external producer.
        // A fresh id still beats no id: the consumer-side work stays correlated
        // even though it can't be linked back to the originating request.
        return UUID.randomUUID().toString();
    }
}
