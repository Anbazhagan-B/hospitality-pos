package com.pos.check.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "check_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    private Check check;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    private String specialInstructions;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    private boolean voided;

    private String voidReason;

    @OneToMany(mappedBy = "checkItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheckItemModifier> modifiers = new ArrayList<>();

    public enum ItemStatus {
        ORDERED,
        SENT_TO_KITCHEN,
        IN_PREPARATION,
        READY,
        SERVED,
        VOIDED
    }

    public void calculateTotalPrice() {
        BigDecimal modifierTotal = modifiers.stream()
                .map(CheckItemModifier::getPriceAdjustment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalPrice = this.unitPrice.add(modifierTotal).multiply(BigDecimal.valueOf(quantity));
    }
}
