package com.pos.payment.dto;

import com.pos.payment.entity.PaymentTransaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    @NotNull(message = "Check ID is required")
    private Long checkId;

    @NotNull(message = "Check number is required")
    private String checkNumber;

    @NotNull(message = "Payment method is required")
    private PaymentTransaction.PaymentMethod paymentMethod;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private BigDecimal tipAmount;

    @NotNull(message = "Organization ID is required")
    private Long organizationId;

    // Credit/Debit Card fields
    private String cardNumber;
    private String expiryMonth;
    private String expiryYear;
    private String cvv;
    private String cardholderName;

    // Gift Card fields
    private String giftCardNumber;
    private String giftCardPin;

    // Loyalty fields
    private String loyaltyAccountId;
    private String loyaltyPin;

    // Casino fields
    private String casinoAccountId;
    private String casinoPin;
}
