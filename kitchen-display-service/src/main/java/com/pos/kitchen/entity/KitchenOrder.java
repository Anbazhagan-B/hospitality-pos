package com.pos.kitchen.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kitchen_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KitchenOrder extends BaseEntity {

    @Column(name = "check_id", nullable = false)
    private Long checkId;

    @Column(nullable = false)
    private String checkNumber;

    private String tableNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Integer priority;

    private LocalDateTime receivedAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "terminal_id")
    private Long terminalId;

    @OneToMany(mappedBy = "kitchenOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<KitchenOrderItem> items = new ArrayList<>();

    public enum OrderStatus {
        RECEIVED,
        IN_PREPARATION,
        READY,
        SERVED,
        CANCELLED
    }

    public void addItem(KitchenOrderItem item) {
        items.add(item);
        item.setKitchenOrder(this);
    }
}
