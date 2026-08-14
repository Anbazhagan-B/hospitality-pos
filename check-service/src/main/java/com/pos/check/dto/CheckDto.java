package com.pos.check.dto;

import com.pos.check.entity.Check;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckDto {
    private Long id;
    private String checkNumber;
    private Check.CheckStatus status;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal tipAmount;
    private Integer guestCount;
    private String tableNumber;
    private Long employeeId;
    private Long terminalId;
    private Long organizationId;
    private Long profitCenterId;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private List<CheckItemDto> items;
    private List<CheckPaymentDto> payments;
}
