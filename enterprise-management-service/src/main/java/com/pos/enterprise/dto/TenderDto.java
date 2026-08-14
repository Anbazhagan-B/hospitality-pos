package com.pos.enterprise.dto;

import com.pos.enterprise.entity.Tender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenderDto {
    private Long id;
    private String name;
    private Tender.TenderType type;
    private String description;
    private boolean active;
    private boolean opensCashDrawer;
    private BigDecimal maxAmount;
    private Long organizationId;
}
