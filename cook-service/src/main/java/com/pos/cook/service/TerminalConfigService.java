package com.pos.cook.service;

import com.pos.cook.dto.TerminalConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalConfigService {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.enterprise-service.url:http://localhost:8082}")
    private String enterpriseServiceUrl;

    @CircuitBreaker(name = "cookService", fallbackMethod = "getTerminalConfigFallback")
    public TerminalConfig generateTerminalConfig(String terminalId, Long organizationId) {
        log.info("Generating terminal configuration for terminal: {} org: {}", terminalId, organizationId);

        // In production, fetch data from Enterprise Management Service
        // For now, generate sample configuration
        return buildSampleConfig(terminalId, organizationId);
    }

    public TerminalConfig getTerminalConfigFallback(String terminalId, Long organizationId, Exception ex) {
        log.error("Circuit breaker fallback for terminal config. Terminal: {}, Error: {}",
                terminalId, ex.getMessage());

        // Return minimal fallback config
        return TerminalConfig.builder()
                .terminalId(terminalId)
                .organizationId(organizationId)
                .version("fallback-1.0")
                .generatedAt(LocalDateTime.now())
                .settings(TerminalConfig.TerminalSettings.builder()
                        .requireEmployeeLogin(true)
                        .printReceipts(true)
                        .enableTips(true)
                        .defaultTaxRate("0.00")
                        .currency("USD")
                        .locale("en-US")
                        .build())
                .build();
    }

    private TerminalConfig buildSampleConfig(String terminalId, Long organizationId) {
        // Build sample menu categories and items
        List<TerminalConfig.CategoryConfig> categories = new ArrayList<>();

        // Appetizers category
        List<TerminalConfig.MenuItemConfig> appetizers = new ArrayList<>();
        appetizers.add(TerminalConfig.MenuItemConfig.builder()
                .id(1L)
                .name("Spring Rolls")
                .description("Crispy vegetable spring rolls")
                .price("8.99")
                .sku("APP-001")
                .modifiers(List.of(
                        TerminalConfig.ModifierConfig.builder()
                                .id(1L).name("Extra Sauce").priceAdjustment("0.50").type("ADD_ON").build()
                ))
                .build());
        appetizers.add(TerminalConfig.MenuItemConfig.builder()
                .id(2L)
                .name("Soup of the Day")
                .description("Chef's daily soup selection")
                .price("6.99")
                .sku("APP-002")
                .modifiers(List.of(
                        TerminalConfig.ModifierConfig.builder()
                                .id(2L).name("Large").priceAdjustment("2.00").type("SIZE").build()
                ))
                .build());

        categories.add(TerminalConfig.CategoryConfig.builder()
                .id(1L)
                .name("Appetizers")
                .description("Start your meal right")
                .displayOrder(1)
                .items(appetizers)
                .build());

        // Main Courses category
        List<TerminalConfig.MenuItemConfig> mainCourses = new ArrayList<>();
        mainCourses.add(TerminalConfig.MenuItemConfig.builder()
                .id(3L)
                .name("Grilled Salmon")
                .description("Atlantic salmon with herbs")
                .price("24.99")
                .sku("MAIN-001")
                .modifiers(List.of(
                        TerminalConfig.ModifierConfig.builder()
                                .id(3L).name("Gluten Free").priceAdjustment("0.00").type("DIETARY").build(),
                        TerminalConfig.ModifierConfig.builder()
                                .id(4L).name("Extra Vegetables").priceAdjustment("3.00").type("SIDE").build()
                ))
                .build());
        mainCourses.add(TerminalConfig.MenuItemConfig.builder()
                .id(4L)
                .name("Ribeye Steak")
                .description("12oz prime ribeye")
                .price("34.99")
                .sku("MAIN-002")
                .modifiers(List.of(
                        TerminalConfig.ModifierConfig.builder()
                                .id(5L).name("Rare").priceAdjustment("0.00").type("PREPARATION").build(),
                        TerminalConfig.ModifierConfig.builder()
                                .id(6L).name("Medium").priceAdjustment("0.00").type("PREPARATION").build(),
                        TerminalConfig.ModifierConfig.builder()
                                .id(7L).name("Well Done").priceAdjustment("0.00").type("PREPARATION").build()
                ))
                .build());

        categories.add(TerminalConfig.CategoryConfig.builder()
                .id(2L)
                .name("Main Courses")
                .description("Signature entrees")
                .displayOrder(2)
                .items(mainCourses)
                .build());

        // Desserts category
        List<TerminalConfig.MenuItemConfig> desserts = new ArrayList<>();
        desserts.add(TerminalConfig.MenuItemConfig.builder()
                .id(5L)
                .name("Chocolate Lava Cake")
                .description("Warm chocolate cake with molten center")
                .price("9.99")
                .sku("DES-001")
                .build());
        desserts.add(TerminalConfig.MenuItemConfig.builder()
                .id(6L)
                .name("Cheesecake")
                .description("New York style cheesecake")
                .price("8.99")
                .sku("DES-002")
                .build());

        categories.add(TerminalConfig.CategoryConfig.builder()
                .id(3L)
                .name("Desserts")
                .description("Sweet endings")
                .displayOrder(3)
                .items(desserts)
                .build());

        // Build tenders
        List<TerminalConfig.TenderConfig> tenders = List.of(
                TerminalConfig.TenderConfig.builder()
                        .id(1L).name("Cash").type("CASH").opensCashDrawer(true).build(),
                TerminalConfig.TenderConfig.builder()
                        .id(2L).name("Credit Card").type("CREDIT_CARD").opensCashDrawer(false).build(),
                TerminalConfig.TenderConfig.builder()
                        .id(3L).name("Debit Card").type("DEBIT_CARD").opensCashDrawer(false).build(),
                TerminalConfig.TenderConfig.builder()
                        .id(4L).name("Gift Card").type("GIFT_CARD").opensCashDrawer(false).build()
        );

        // Build terminal settings
        TerminalConfig.TerminalSettings settings = TerminalConfig.TerminalSettings.builder()
                .requireEmployeeLogin(true)
                .printReceipts(true)
                .enableTips(true)
                .defaultTaxRate("8.25")
                .currency("USD")
                .locale("en-US")
                .build();

        return TerminalConfig.builder()
                .terminalId(terminalId)
                .terminalName("Terminal " + terminalId)
                .organizationId(organizationId)
                .organizationName("Sample Restaurant")
                .profitCenterId(1L)
                .profitCenterName("Main Dining")
                .version("1.0.0")
                .generatedAt(LocalDateTime.now())
                .menu(TerminalConfig.MenuConfig.builder().categories(categories).build())
                .tenders(tenders)
                .settings(settings)
                .build();
    }
}
