package com.pos.payment.service;

import com.pos.payment.dto.PaymentRequest;
import com.pos.payment.dto.PaymentResponse;
import com.pos.payment.entity.PaymentTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service("creditCardProcessor")
public class CreditCardProcessor implements PaymentProcessor {

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing credit card payment for check: {}", request.getCheckNumber());

        // Simulate card processing (in production, integrate with actual payment gateway)
        String transactionId = generateTransactionId();
        String authCode = generateAuthCode();

        // Mask card number
        String cardLastFour = maskCardNumber(request.getCardNumber());
        String cardType = detectCardType(request.getCardNumber());

        // Simulate approval (in production, this would be an API call)
        boolean approved = simulatePaymentApproval(request);

        PaymentTransaction.TransactionStatus status = approved ?
                PaymentTransaction.TransactionStatus.APPROVED :
                PaymentTransaction.TransactionStatus.DECLINED;

        return PaymentResponse.builder()
                .transactionId(transactionId)
                .checkId(request.getCheckId())
                .checkNumber(request.getCheckNumber())
                .paymentMethod(PaymentTransaction.PaymentMethod.CREDIT_CARD)
                .amount(request.getAmount())
                .tipAmount(request.getTipAmount())
                .status(status)
                .authorizationCode(approved ? authCode : null)
                .referenceNumber(transactionId)
                .cardLastFour(cardLastFour)
                .cardType(cardType)
                .errorCode(approved ? null : "DECLINED")
                .errorMessage(approved ? null : "Card was declined")
                .processedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount) {
        log.info("Processing refund for transaction: {}", transactionId);

        return PaymentResponse.builder()
                .transactionId(generateTransactionId())
                .status(PaymentTransaction.TransactionStatus.REFUNDED)
                .amount(amount)
                .referenceNumber(transactionId)
                .processedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public PaymentResponse voidPayment(String transactionId) {
        log.info("Voiding transaction: {}", transactionId);

        return PaymentResponse.builder()
                .transactionId(transactionId)
                .status(PaymentTransaction.TransactionStatus.VOIDED)
                .processedAt(LocalDateTime.now())
                .build();
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateAuthCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return cardNumber.substring(cardNumber.length() - 4);
    }

    private String detectCardType(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return "UNKNOWN";
        }
        if (cardNumber.startsWith("4")) {
            return "VISA";
        } else if (cardNumber.startsWith("5")) {
            return "MASTERCARD";
        } else if (cardNumber.startsWith("3")) {
            return "AMEX";
        } else if (cardNumber.startsWith("6")) {
            return "DISCOVER";
        }
        return "OTHER";
    }

    private boolean simulatePaymentApproval(PaymentRequest request) {
        // Simulate 95% approval rate
        return Math.random() < 0.95;
    }
}
