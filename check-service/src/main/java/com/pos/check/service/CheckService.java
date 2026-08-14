package com.pos.check.service;

import com.pos.check.dto.*;
import com.pos.check.entity.Check;
import com.pos.check.entity.CheckItem;
import com.pos.check.entity.CheckItemModifier;
import com.pos.check.event.OrderEvent;
import com.pos.check.repository.CheckStore;
import com.pos.common.dto.PageResponse;
import com.pos.common.exception.BadRequestException;
import com.pos.common.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckService {

    private final CheckStore checkStore;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public CheckDto createCheck(CreateCheckRequest request) {
        log.info("Creating new check for employee: {}", request.getEmployeeId());

        // The check number is assigned by the store, together with the id, from
        // a single sequence value - see CheckStore.save.
        Check check = Check.builder()
                .status(Check.CheckStatus.OPEN)
                .subtotal(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .employeeId(request.getEmployeeId())
                .terminalId(request.getTerminalId())
                .organizationId(request.getOrganizationId())
                .profitCenterId(request.getProfitCenterId())
                .tableNumber(request.getTableNumber())
                .guestCount(request.getGuestCount())
                .openedAt(LocalDateTime.now())
                .build();

        check = checkStore.save(check);
        log.info("Check created with number: {}", check.getCheckNumber());

        return toDto(check);
    }

    public CheckDto getCheckById(Long id) {
        Check check = checkStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Check", "id", id));
        return toDto(check);
    }

    public CheckDto getCheckByNumber(String checkNumber) {
        Check check = checkStore.findByCheckNumber(checkNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Check", "checkNumber", checkNumber));
        return toDto(check);
    }

    public PageResponse<CheckDto> getChecksByOrganization(Long organizationId, Pageable pageable) {
        Page<Check> page = checkStore.findByOrganizationId(organizationId, pageable);
        return toPageResponse(page);
    }

    public List<CheckDto> getOpenChecks(Long organizationId) {
        return checkStore.findByOrganizationAndStatus(organizationId, Check.CheckStatus.OPEN)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CircuitBreaker(name = "checkService", fallbackMethod = "addItemFallback")
    public CheckDto addItem(Long checkId, AddItemRequest request) {
        log.info("Adding item to check: {}", checkId);

        Check check = checkStore.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Check", "id", checkId));

        if (check.getStatus() == Check.CheckStatus.CLOSED ||
            check.getStatus() == Check.CheckStatus.VOID) {
            throw new BadRequestException("Cannot add items to a closed or voided check");
        }

        CheckItem item = CheckItem.builder()
                .menuItemId(request.getMenuItemId())
                .itemName(request.getItemName())
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .specialInstructions(request.getSpecialInstructions())
                .status(CheckItem.ItemStatus.ORDERED)
                .voided(false)
                .build();

        if (request.getModifiers() != null) {
            List<CheckItemModifier> modifiers = request.getModifiers().stream()
                    .map(m -> CheckItemModifier.builder()
                            .modifierId(m.getModifierId())
                            .modifierName(m.getModifierName())
                            .priceAdjustment(m.getPriceAdjustment())
                            .checkItem(item)
                            .build())
                    .collect(Collectors.toList());
            item.getModifiers().addAll(modifiers);
        }

        item.calculateTotalPrice();
        check.addItem(item);
        check.setStatus(Check.CheckStatus.IN_PROGRESS);

        check = checkStore.save(check);

        publishOrderEvent(check, OrderEvent.EventType.ITEM_ADDED);

        log.info("Item added to check: {}", checkId);
        return toDto(check);
    }

    public CheckDto addItemFallback(Long checkId, AddItemRequest request, Exception ex) {
        log.error("Circuit breaker fallback for addItem. Check ID: {}, Error: {}", checkId, ex.getMessage());
        throw new BadRequestException("Service temporarily unavailable. Please try again.");
    }

    @Transactional
    public CheckDto sendToKitchen(Long checkId) {
        log.info("Sending check {} to kitchen", checkId);

        Check check = checkStore.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Check", "id", checkId));

        check.getItems().stream()
                .filter(item -> item.getStatus() == CheckItem.ItemStatus.ORDERED)
                .forEach(item -> item.setStatus(CheckItem.ItemStatus.SENT_TO_KITCHEN));

        check = checkStore.save(check);

        publishOrderEvent(check, OrderEvent.EventType.ORDER_PLACED);

        return toDto(check);
    }

    @Transactional
    public CheckDto voidItem(Long checkId, Long itemId, String reason) {
        log.info("Voiding item {} on check {}", itemId, checkId);

        Check check = checkStore.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Check", "id", checkId));

        CheckItem item = check.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CheckItem", "id", itemId));

        item.setVoided(true);
        item.setVoidReason(reason);
        item.setStatus(CheckItem.ItemStatus.VOIDED);

        check.recalculateTotals();
        check = checkStore.save(check);

        publishOrderEvent(check, OrderEvent.EventType.ITEM_REMOVED);

        return toDto(check);
    }

    @Transactional
    public CheckDto closeCheck(Long checkId) {
        log.info("Closing check {}", checkId);

        Check check = checkStore.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Check", "id", checkId));

        if (check.getPaidAmount().compareTo(check.getTotalAmount()) < 0) {
            throw new BadRequestException("Check is not fully paid");
        }

        check.setStatus(Check.CheckStatus.CLOSED);
        check.setClosedAt(LocalDateTime.now());

        check = checkStore.save(check);
        return toDto(check);
    }

    private void publishOrderEvent(Check check, OrderEvent.EventType eventType) {
        List<OrderEvent.OrderItemEvent> itemEvents = check.getItems().stream()
                .filter(item -> !item.isVoided())
                .map(item -> OrderEvent.OrderItemEvent.builder()
                        .itemId(item.getId())
                        .menuItemId(item.getMenuItemId())
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .specialInstructions(item.getSpecialInstructions())
                        .status(item.getStatus().name())
                        .modifiers(item.getModifiers().stream()
                                .map(m -> OrderEvent.ModifierEvent.builder()
                                        .modifierId(m.getModifierId())
                                        .modifierName(m.getModifierName())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .checkId(check.getId())
                .checkNumber(check.getCheckNumber())
                .organizationId(check.getOrganizationId())
                .terminalId(check.getTerminalId())
                .tableNumber(check.getTableNumber())
                .items(itemEvents)
                .timestamp(LocalDateTime.now())
                .build();

        orderEventPublisher.publishOrderEvent(event);
    }


    private CheckDto toDto(Check check) {
        return CheckDto.builder()
                .id(check.getId())
                .checkNumber(check.getCheckNumber())
                .status(check.getStatus())
                .subtotal(check.getSubtotal())
                .taxAmount(check.getTaxAmount())
                .discountAmount(check.getDiscountAmount())
                .totalAmount(check.getTotalAmount())
                .paidAmount(check.getPaidAmount())
                .tipAmount(check.getTipAmount())
                .guestCount(check.getGuestCount())
                .tableNumber(check.getTableNumber())
                .employeeId(check.getEmployeeId())
                .terminalId(check.getTerminalId())
                .organizationId(check.getOrganizationId())
                .profitCenterId(check.getProfitCenterId())
                .openedAt(check.getOpenedAt())
                .closedAt(check.getClosedAt())
                .items(check.getItems().stream()
                        .map(this::toItemDto)
                        .collect(Collectors.toList()))
                .payments(check.getPayments().stream()
                        .map(this::toPaymentDto)
                        .collect(Collectors.toList()))
                .build();
    }

    private CheckItemDto toItemDto(CheckItem item) {
        return CheckItemDto.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItemId())
                .itemName(item.getItemName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .specialInstructions(item.getSpecialInstructions())
                .status(item.getStatus())
                .voided(item.isVoided())
                .voidReason(item.getVoidReason())
                .modifiers(item.getModifiers().stream()
                        .map(m -> CheckItemModifierDto.builder()
                                .id(m.getId())
                                .modifierId(m.getModifierId())
                                .modifierName(m.getModifierName())
                                .priceAdjustment(m.getPriceAdjustment())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private CheckPaymentDto toPaymentDto(com.pos.check.entity.CheckPayment payment) {
        return CheckPaymentDto.builder()
                .id(payment.getId())
                .tenderId(payment.getTenderId())
                .tenderName(payment.getTenderName())
                .paymentType(payment.getPaymentType())
                .amount(payment.getAmount())
                .tipAmount(payment.getTipAmount())
                .referenceNumber(payment.getReferenceNumber())
                .authorizationCode(payment.getAuthorizationCode())
                .status(payment.getStatus())
                .processedAt(payment.getProcessedAt())
                .voided(payment.isVoided())
                .voidReason(payment.getVoidReason())
                .build();
    }

    private PageResponse<CheckDto> toPageResponse(Page<Check> page) {
        return PageResponse.<CheckDto>builder()
                .content(page.getContent().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
