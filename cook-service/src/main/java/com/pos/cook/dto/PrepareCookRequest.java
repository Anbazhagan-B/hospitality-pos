package com.pos.cook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrepareCookRequest {

    @NotNull(message = "Organization ID is required")
    private Long organizationId;

    @NotNull(message = "Profit Center ID is required")
    private Long profitCenterId;

    private String terminalId;

    @NotEmpty(message = "At least one order is required")
    @Valid
    private List<CookOrder> orders;

    private String outputFileName;
}
