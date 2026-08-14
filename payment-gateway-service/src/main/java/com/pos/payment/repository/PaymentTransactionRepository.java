package com.pos.payment.repository;

import com.pos.payment.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    List<PaymentTransaction> findByCheckId(Long checkId);

    Page<PaymentTransaction> findByOrganizationId(Long organizationId, Pageable pageable);

    List<PaymentTransaction> findByCheckIdAndStatus(Long checkId, PaymentTransaction.TransactionStatus status);

    List<PaymentTransaction> findByProcessedAtBetween(LocalDateTime start, LocalDateTime end);
}
