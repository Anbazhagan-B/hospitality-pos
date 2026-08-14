package com.pos.payment.service;

import com.pos.payment.dto.PaymentRequest;
import com.pos.payment.dto.PaymentResponse;

public interface PaymentProcessor {
    PaymentResponse processPayment(PaymentRequest request);
    PaymentResponse refundPayment(String transactionId, java.math.BigDecimal amount);
    PaymentResponse voidPayment(String transactionId);
}
