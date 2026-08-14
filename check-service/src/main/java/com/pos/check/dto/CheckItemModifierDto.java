package com.pos.check.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckItemModifierDto {
    private Long id;
    private Long modifierId;
    private String modifierName;
    private BigDecimal priceAdjustment;
}
