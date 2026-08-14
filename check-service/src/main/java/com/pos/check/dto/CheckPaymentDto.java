package com.pos.check.dto;

import com.pos.check.entity.CheckPayment;
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
public class CheckPaymentDto {
    private Long id;
    private Long tenderId;
    private String tenderName;
    private CheckPayment.PaymentType paymentType;
    private BigDecimal amount;
    private BigDecimal tipAmount;
    private String referenceNumber;
    private String authorizationCode;
    private CheckPayment.PaymentStatus status;
    private LocalDateTime processedAt;
    private boolean voided;
    private String voidReason;
}
