package com.pos.kitchen.service;

import com.pos.kitchen.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final KitchenOrderService kitchenOrderService;

    @KafkaListener(topics = "${kafka.topics.order-events:order-events}", groupId = "${spring.kafka.consumer.group-id:kitchen-display-group}")
    public void consumeOrderEvent(OrderEvent event) {
        log.info("Received order event: {} for check: {}", event.getEventType(), event.getCheckNumber());

        try {
            switch (event.getEventType()) {
                case ORDER_PLACED -> kitchenOrderService.createOrUpdateOrder(event);
                case ORDER_UPDATED -> kitchenOrderService.updateOrder(event);
                case ITEM_ADDED -> kitchenOrderService.addItemToOrder(event);
                case ITEM_REMOVED -> kitchenOrderService.removeItemFromOrder(event);
                case ITEM_MODIFIED -> kitchenOrderService.modifyItem(event);
                case ORDER_CANCELLED -> kitchenOrderService.cancelOrder(event.getCheckId());
                default -> log.warn("Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing order event for check: {}", event.getCheckNumber(), e);
        }
    }
}
