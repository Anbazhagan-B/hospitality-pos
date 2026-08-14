package com.pos.payment.service;

import com.pos.common.exception.BadRequestException;
import com.pos.common.exception.ResourceNotFoundException;
import com.pos.payment.dto.PaymentRequest;
import com.pos.payment.dto.PaymentResponse;
import com.pos.payment.entity.PaymentTransaction;
import com.pos.payment.repository.PaymentTransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final Map<String, PaymentProcessor> paymentProcessors;

    @Transactional
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for check: {} with method: {}",
                request.getCheckNumber(), request.getPaymentMethod());

        PaymentProcessor processor = getProcessor(request.getPaymentMethod());
        PaymentResponse response = processor.processPayment(request);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionId(response.getTransactionId())
                .checkId(request.getCheckId())
                .checkNumber(request.getCheckNumber())
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .tipAmount(request.getTipAmount())
                .status(response.getStatus())
                .authorizationCode(response.getAuthorizationCode())
                .referenceNumber(response.getReferenceNumber())
                .cardLastFour(response.getCardLastFour())
                .cardType(response.getCardType())
                .loyaltyAccountId(request.getLoyaltyAccountId())
                .giftCardNumber(maskGiftCard(request.getGiftCardNumber()))
                .casinoAccountId(request.getCasinoAccountId())
                .errorCode(response.getErrorCode())
                .errorMessage(response.getErrorMessage())
                .processedAt(LocalDateTime.now())
                .organizationId(request.getOrganizationId())
                .build();

        transactionRepository.save(transaction);
        log.info("Payment transaction saved: {}", response.getTransactionId());

        return response;
    }

    public PaymentResponse processPaymentFallback(PaymentRequest request, Exception ex) {
        log.error("Payment processing failed for check: {}. Error: {}",
                request.getCheckNumber(), ex.getMessage());

        return PaymentResponse.builder()
                .transactionId("ERR-" + UUID.randomUUID().toString().substring(0, 8))
                .checkId(request.getCheckId())
                .checkNumber(request.getCheckNumber())
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .status(PaymentTransaction.TransactionStatus.ERROR)
                .errorCode("SERVICE_UNAVAILABLE")
                .errorMessage("Payment service temporarily unavailable. Please try again.")
                .processedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount) {
        log.info("Processing refund for transaction: {}", transactionId);

        PaymentTransaction original = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "transactionId", transactionId));

        if (original.getStatus() != PaymentTransaction.TransactionStatus.APPROVED) {
            throw new BadRequestException("Cannot refund a non-approved transaction");
        }

        if (amount.compareTo(original.getAmount()) > 0) {
            throw new BadRequestException("Refund amount cannot exceed original transaction amount");
        }

        PaymentProcessor processor = getProcessor(original.getPaymentMethod());
        PaymentResponse response = processor.refundPayment(transactionId, amount);

        PaymentTransaction refundTxn = PaymentTransaction.builder()
                .transactionId(response.getTransactionId())
                .checkId(original.getCheckId())
                .checkNumber(original.getCheckNumber())
                .paymentMethod(original.getPaymentMethod())
                .amount(amount.negate())
                .status(PaymentTransaction.TransactionStatus.REFUNDED)
                .referenceNumber(transactionId)
                .processedAt(LocalDateTime.now())
                .organizationId(original.getOrganizationId())
                .build();

        transactionRepository.save(refundTxn);

        return response;
    }

    @Transactional
    public PaymentResponse voidPayment(String transactionId) {
        log.info("Voiding transaction: {}", transactionId);

        PaymentTransaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "transactionId", transactionId));

        if (transaction.getStatus() != PaymentTransaction.TransactionStatus.APPROVED) {
            throw new BadRequestException("Cannot void a non-approved transaction");
        }

        PaymentProcessor processor = getProcessor(transaction.getPaymentMethod());
        PaymentResponse response = processor.voidPayment(transactionId);

        transaction.setStatus(PaymentTransaction.TransactionStatus.VOIDED);
        transactionRepository.save(transaction);

        return response;
    }

    public PaymentResponse getTransaction(String transactionId) {
        PaymentTransaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "transactionId", transactionId));

        return toResponse(transaction);
    }

    public List<PaymentResponse> getTransactionsByCheck(Long checkId) {
        return transactionRepository.findByCheckId(checkId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PaymentProcessor getProcessor(PaymentTransaction.PaymentMethod method) {
        String processorName = switch (method) {
            case CREDIT_CARD, DEBIT_CARD -> "creditCardProcessor";
            case GIFT_CARD -> "giftCardProcessor";
            case LOYALTY -> "loyaltyProcessor";
            case CASINO -> "casinoProcessor";
            case CASH -> "cashProcessor";
        };

        PaymentProcessor processor = paymentProcessors.get(processorName);
        if (processor == null) {
            // Fall back to credit card processor for simulation
            processor = paymentProcessors.get("creditCardProcessor");
        }
        return processor;
    }

    private String maskGiftCard(String giftCardNumber) {
        if (giftCardNumber == null || giftCardNumber.length() < 4) {
            return giftCardNumber;
        }
        return "****" + giftCardNumber.substring(giftCardNumber.length() - 4);
    }

    private PaymentResponse toResponse(PaymentTransaction transaction) {
        return PaymentResponse.builder()
                .transactionId(transaction.getTransactionId())
                .checkId(transaction.getCheckId())
                .checkNumber(transaction.getCheckNumber())
                .paymentMethod(transaction.getPaymentMethod())
                .amount(transaction.getAmount())
                .tipAmount(transaction.getTipAmount())
                .status(transaction.getStatus())
                .authorizationCode(transaction.getAuthorizationCode())
                .referenceNumber(transaction.getReferenceNumber())
                .cardLastFour(transaction.getCardLastFour())
                .cardType(transaction.getCardType())
                .errorCode(transaction.getErrorCode())
                .errorMessage(transaction.getErrorMessage())
                .processedAt(transaction.getProcessedAt())
                .build();
    }
}
