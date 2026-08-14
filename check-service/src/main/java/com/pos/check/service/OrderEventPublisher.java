package com.pos.check.service;

import com.pos.check.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${kafka.topics.order-events:order-events}")
    private String orderEventsTopic;

    /**
     * Publishes an order event without ever failing the caller.
     *
     * <p>The check has already been persisted by the time this runs. If the
     * broker is unreachable, the correct outcome is a saved check and a loud
     * error - not a failed HTTP response. Letting the exception propagate meant
     * {@code addItem} hit its circuit-breaker fallback and returned 400, telling
     * the terminal to retry an operation that had in fact succeeded; a retry
     * would then add the line twice.
     *
     * <p>This is containment, not a fix. Swallowing the failure means the
     * kitchen never receives the order and only a log line records it - the
     * event is genuinely lost. The real fix is to stop publishing from the
     * request path at all: write the event in the same atomic operation as the
     * check and let a relay forward it. With DynamoDB that falls out for free,
     * because the table has Streams enabled and the change log <em>is</em> the
     * commit - see stream_view_type in infrastructure/terraform/dynamodb.tf.
     */
    public void publishOrderEvent(OrderEvent event) {
        log.info("Publishing order event: {} for check: {}", event.getEventType(), event.getCheckNumber());

        try {
            CompletableFuture<SendResult<String, OrderEvent>> future =
                    kafkaTemplate.send(orderEventsTopic, event.getCheckNumber(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Order event sent successfully for check: {} to partition: {}",
                            event.getCheckNumber(),
                            result.getRecordMetadata().partition());
                } else {
                    log.error("ORDER EVENT LOST for check: {} - the check is saved but the kitchen "
                            + "was not notified", event.getCheckNumber(), ex);
                }
            });
        } catch (Exception ex) {
            // send() itself throws when it cannot resolve topic metadata within
            // max.block.ms, before any future exists.
            log.error("ORDER EVENT LOST for check: {} - broker unreachable, the check is saved but "
                    + "the kitchen was not notified", event.getCheckNumber(), ex);
        }
    }
}
