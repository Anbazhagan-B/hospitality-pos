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

    public void publishOrderEvent(OrderEvent event) {
        log.info("Publishing order event: {} for check: {}", event.getEventType(), event.getCheckNumber());

        CompletableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(orderEventsTopic, event.getCheckNumber(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Order event sent successfully for check: {} to partition: {}",
                        event.getCheckNumber(),
                        result.getRecordMetadata().partition());
            } else {
                log.error("Failed to send order event for check: {}", event.getCheckNumber(), ex);
            }
        });
    }
}
