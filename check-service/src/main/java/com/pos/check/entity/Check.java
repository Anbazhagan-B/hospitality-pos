package com.pos.check.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Check extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String checkNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CheckStatus status;

    @Column(nullable = false)
    private BigDecimal subtotal;

    private BigDecimal taxAmount;

    private BigDecimal discountAmount;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    private BigDecimal paidAmount;

    private BigDecimal tipAmount;

    private Integer guestCount;

    private String tableNumber;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "terminal_id", nullable = false)
    private Long terminalId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "profit_center_id")
    private Long profitCenterId;

    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "check", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheckItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "check", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheckPayment> payments = new ArrayList<>();

    public enum CheckStatus {
        OPEN,
        IN_PROGRESS,
        READY_FOR_PAYMENT,
        PARTIALLY_PAID,
        PAID,
        CLOSED,
        VOID
    }

    public void addItem(CheckItem item) {
        items.add(item);
        item.setCheck(this);
        recalculateTotals();
    }

    public void removeItem(CheckItem item) {
        items.remove(item);
        item.setCheck(null);
        recalculateTotals();
    }

    public void recalculateTotals() {
        this.subtotal = items.stream()
                .filter(item -> !item.isVoided())
                .map(CheckItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (this.discountAmount == null) {
            this.discountAmount = BigDecimal.ZERO;
        }
        if (this.taxAmount == null) {
            this.taxAmount = BigDecimal.ZERO;
        }

        this.totalAmount = this.subtotal.add(this.taxAmount).subtract(this.discountAmount);

        this.paidAmount = payments.stream()
                .filter(payment -> !payment.isVoided())
                .map(CheckPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
