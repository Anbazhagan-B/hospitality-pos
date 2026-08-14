package com.pos.enterprise.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tenders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tender extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenderType type;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    private boolean opensCashDrawer;

    private BigDecimal maxAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    public enum TenderType {
        CASH,
        CREDIT_CARD,
        DEBIT_CARD,
        GIFT_CARD,
        LOYALTY,
        CASINO,
        CHECK
    }
}
