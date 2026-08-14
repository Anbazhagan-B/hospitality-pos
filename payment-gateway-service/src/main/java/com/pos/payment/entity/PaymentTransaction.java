package com.pos.payment.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(name = "check_id", nullable = false)
    private Long checkId;

    @Column(nullable = false)
    private String checkNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private BigDecimal amount;

    private BigDecimal tipAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String authorizationCode;

    private String referenceNumber;

    private String cardLastFour;

    private String cardType;

    private String loyaltyAccountId;

    private String giftCardNumber;

    private String casinoAccountId;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime processedAt;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    public enum PaymentMethod {
        CREDIT_CARD,
        DEBIT_CARD,
        GIFT_CARD,
        LOYALTY,
        CASINO,
        CASH
    }

    public enum TransactionStatus {
        PENDING,
        APPROVED,
        DECLINED,
        ERROR,
        VOIDED,
        REFUNDED
    }
}
