package com.pos.kitchen.dto;

import com.pos.kitchen.entity.KitchenOrder;
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
public class KitchenOrderDto {
    private Long id;
    private Long checkId;
    private String checkNumber;
    private String tableNumber;
    private KitchenOrder.OrderStatus status;
    private Integer priority;
    private LocalDateTime receivedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long organizationId;
    private Long terminalId;
    private List<KitchenOrderItemDto> items;
    private long elapsedTimeSeconds;
}
