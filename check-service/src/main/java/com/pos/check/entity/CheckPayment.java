package com.pos.check.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "check_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    private Check check;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(nullable = false)
    private String tenderName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    @Column(nullable = false)
    private BigDecimal amount;

    private BigDecimal tipAmount;

    private String referenceNumber;

    private String authorizationCode;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime processedAt;

    private boolean voided;

    private String voidReason;

    public enum PaymentType {
        CASH,
        CREDIT_CARD,
        DEBIT_CARD,
        GIFT_CARD,
        LOYALTY,
        CASINO,
        CHECK
    }

    public enum PaymentStatus {
        PENDING,
        APPROVED,
        DECLINED,
        VOIDED,
        REFUNDED
    }
}
