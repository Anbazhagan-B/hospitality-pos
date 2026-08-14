package com.pos.payment.controller;

import com.pos.common.dto.ApiResponse;
import com.pos.payment.dto.PaymentRequest;
import com.pos.payment.dto.PaymentResponse;
import com.pos.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Gateway", description = "Payment processing operations")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Process Payment", description = "Process a payment transaction")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);
        String message = response.getStatus().name().equals("APPROVED") ?
                "Payment processed successfully" : "Payment was declined";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get Transaction", description = "Retrieve a transaction by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getTransaction(
            @PathVariable String transactionId) {
        PaymentResponse response = paymentService.getTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/check/{checkId}")
    @Operation(summary = "Get Transactions by Check", description = "Retrieve all transactions for a check")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getTransactionsByCheck(
            @PathVariable Long checkId) {
        List<PaymentResponse> responses = paymentService.getTransactionsByCheck(checkId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping("/{transactionId}/refund")
    @Operation(summary = "Refund Payment", description = "Refund a payment transaction")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            @PathVariable String transactionId,
            @RequestParam BigDecimal amount) {
        PaymentResponse response = paymentService.refundPayment(transactionId, amount);
        return ResponseEntity.ok(ApiResponse.success(response, "Refund processed successfully"));
    }

    @PostMapping("/{transactionId}/void")
    @Operation(summary = "Void Payment", description = "Void a payment transaction")
    public ResponseEntity<ApiResponse<PaymentResponse>> voidPayment(
            @PathVariable String transactionId) {
        PaymentResponse response = paymentService.voidPayment(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment voided successfully"));
    }
}
