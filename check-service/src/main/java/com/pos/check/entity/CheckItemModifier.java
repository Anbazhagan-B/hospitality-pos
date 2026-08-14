package com.pos.check.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "check_item_modifiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckItemModifier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_item_id", nullable = false)
    private CheckItem checkItem;

    @Column(name = "modifier_id", nullable = false)
    private Long modifierId;

    @Column(nullable = false)
    private String modifierName;

    @Column(nullable = false)
    private BigDecimal priceAdjustment;
}
