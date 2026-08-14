package com.pos.check.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {
    private String eventId;
    private EventType eventType;
    private Long checkId;
    private String checkNumber;
    private Long organizationId;
    private Long terminalId;
    private String tableNumber;
    private List<OrderItemEvent> items;
    private LocalDateTime timestamp;

    public enum EventType {
        ORDER_PLACED,
        ORDER_UPDATED,
        ITEM_ADDED,
        ITEM_REMOVED,
        ITEM_MODIFIED,
        ORDER_CANCELLED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemEvent {
        private Long itemId;
        private Long menuItemId;
        private String itemName;
        private Integer quantity;
        private String specialInstructions;
        private String status;
        private List<ModifierEvent> modifiers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModifierEvent {
        private Long modifierId;
        private String modifierName;
    }
}
