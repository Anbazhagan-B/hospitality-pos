package com.pos.kitchen.dto;

import com.pos.kitchen.entity.KitchenOrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KitchenOrderItemDto {
    private Long id;
    private Long checkItemId;
    private Long menuItemId;
    private String itemName;
    private Integer quantity;
    private String specialInstructions;
    private String modifiers;
    private KitchenOrderItem.ItemStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
