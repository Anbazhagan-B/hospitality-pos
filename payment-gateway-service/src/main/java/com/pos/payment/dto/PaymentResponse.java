package com.pos.payment.dto;

import com.pos.payment.entity.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String transactionId;
    private Long checkId;
    private String checkNumber;
    private PaymentTransaction.PaymentMethod paymentMethod;
    private BigDecimal amount;
    private BigDecimal tipAmount;
    private PaymentTransaction.TransactionStatus status;
    private String authorizationCode;
    private String referenceNumber;
    private String cardLastFour;
    private String cardType;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime processedAt;
}
