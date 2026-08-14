package com.pos.kitchen.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kitchen_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KitchenOrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_order_id", nullable = false)
    private KitchenOrder kitchenOrder;

    @Column(name = "check_item_id", nullable = false)
    private Long checkItemId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    private String specialInstructions;

    private String modifiers;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    public enum ItemStatus {
        PENDING,
        IN_PREPARATION,
        READY,
        SERVED,
        CANCELLED
    }
}
