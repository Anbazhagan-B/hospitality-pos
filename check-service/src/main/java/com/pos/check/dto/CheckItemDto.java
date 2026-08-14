package com.pos.check.dto;

import com.pos.check.entity.CheckItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckItemDto {
    private Long id;
    private Long menuItemId;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String specialInstructions;
    private CheckItem.ItemStatus status;
    private boolean voided;
    private String voidReason;
    private List<CheckItemModifierDto> modifiers;
}
