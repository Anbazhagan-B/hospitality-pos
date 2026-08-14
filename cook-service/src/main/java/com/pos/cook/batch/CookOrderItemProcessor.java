package com.pos.cook.batch;

import com.pos.cook.dto.CookOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;

@Slf4j
public class CookOrderItemProcessor implements ItemProcessor<CookOrder, CookOrder> {

    private static final String STATUS_PREPARED = "PREPARED";
    private static final String ITEM_STATUS_READY = "READY_TO_COOK";

    @Override
    public CookOrder process(@NonNull CookOrder order) {
        log.debug("Processing cook order: {}", order.getOrderId());

        // Update order status to PREPARED
        order.setStatus(STATUS_PREPARED);
        order.setPreparedAt(LocalDateTime.now());

        // Update each item's preparation status
        if (order.getItems() != null) {
            order.getItems().forEach(item -> {
                item.setPreparationStatus(ITEM_STATUS_READY);

                // Calculate estimated prep time based on item complexity
                if (item.getEstimatedPrepTimeMinutes() == null) {
                    int baseTime = 5;
                    int modifierTime = item.getModifiers() != null ? item.getModifiers().size() * 2 : 0;
                    item.setEstimatedPrepTimeMinutes(baseTime + modifierTime);
                }
            });
        }

        log.info("Order {} processed and marked as {}", order.getOrderId(), STATUS_PREPARED);
        return order;
    }
}
