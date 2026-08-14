package com.pos.enterprise.dto;

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
public class MenuItemDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private boolean active;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private Long organizationId;
    private List<MenuItemModifierDto> modifiers;
}
