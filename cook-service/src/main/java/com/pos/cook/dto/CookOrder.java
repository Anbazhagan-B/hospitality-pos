package com.pos.cook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookOrder {

    private String orderId;
    private String orderNumber;
    private Long organizationId;
    private Long profitCenterId;
    private String terminalId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime preparedAt;
    private String priority;
    private List<CookOrderItem> items;
    private String notes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CookOrderItem {
        private Long itemId;
        private String itemName;
        private String sku;
        private Integer quantity;
        private BigDecimal price;
        private String preparationStatus;
        private List<ItemModifier> modifiers;
        private String specialInstructions;
        private Integer estimatedPrepTimeMinutes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemModifier {
        private Long modifierId;
        private String name;
        private String type;
        private BigDecimal priceAdjustment;
    }
}
