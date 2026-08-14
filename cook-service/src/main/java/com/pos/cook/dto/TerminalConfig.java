package com.pos.cook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerminalConfig {
    private String terminalId;
    private String terminalName;
    private Long organizationId;
    private String organizationName;
    private Long profitCenterId;
    private String profitCenterName;
    private String version;
    private LocalDateTime generatedAt;
    private MenuConfig menu;
    private List<TenderConfig> tenders;
    private TerminalSettings settings;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MenuConfig {
        private List<CategoryConfig> categories;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryConfig {
        private Long id;
        private String name;
        private String description;
        private Integer displayOrder;
        private List<MenuItemConfig> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MenuItemConfig {
        private Long id;
        private String name;
        private String description;
        private String price;
        private String sku;
        private String imageUrl;
        private List<ModifierConfig> modifiers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModifierConfig {
        private Long id;
        private String name;
        private String description;
        private String priceAdjustment;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenderConfig {
        private Long id;
        private String name;
        private String type;
        private boolean opensCashDrawer;
        private String maxAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TerminalSettings {
        private boolean requireEmployeeLogin;
        private boolean printReceipts;
        private boolean enableTips;
        private String defaultTaxRate;
        private String currency;
        private String locale;
    }
}
