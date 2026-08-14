package com.pos.check.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCheckRequest {
    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Terminal ID is required")
    private Long terminalId;

    @NotNull(message = "Organization ID is required")
    private Long organizationId;

    private Long profitCenterId;

    private String tableNumber;

    private Integer guestCount;
}
