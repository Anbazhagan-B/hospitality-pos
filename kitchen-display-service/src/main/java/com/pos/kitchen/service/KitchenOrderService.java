package com.pos.kitchen.service;

import com.pos.common.exception.ResourceNotFoundException;
import com.pos.kitchen.dto.KitchenOrderDto;
import com.pos.kitchen.dto.KitchenOrderItemDto;
import com.pos.kitchen.entity.KitchenOrder;
import com.pos.kitchen.entity.KitchenOrderItem;
import com.pos.kitchen.event.OrderEvent;
import com.pos.kitchen.repository.KitchenOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderService {

    private final KitchenOrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void createOrUpdateOrder(OrderEvent event) {
        log.info("Creating/updating kitchen order for check: {}", event.getCheckNumber());

        KitchenOrder order = orderRepository.findByCheckId(event.getCheckId())
                .orElse(KitchenOrder.builder()
                        .checkId(event.getCheckId())
                        .checkNumber(event.getCheckNumber())
                        .tableNumber(event.getTableNumber())
                        .organizationId(event.getOrganizationId())
                        .terminalId(event.getTerminalId())
                        .status(KitchenOrder.OrderStatus.RECEIVED)
                        .priority(0)
                        .receivedAt(LocalDateTime.now())
                        .build());

        order.getItems().clear();

        for (OrderEvent.OrderItemEvent itemEvent : event.getItems()) {
            KitchenOrderItem item = KitchenOrderItem.builder()
                    .checkItemId(itemEvent.getItemId())
                    .menuItemId(itemEvent.getMenuItemId())
                    .itemName(itemEvent.getItemName())
                    .quantity(itemEvent.getQuantity())
                    .specialInstructions(itemEvent.getSpecialInstructions())
                    .modifiers(formatModifiers(itemEvent.getModifiers()))
                    .status(KitchenOrderItem.ItemStatus.PENDING)
                    .build();
            order.addItem(item);
        }

        order = orderRepository.save(order);
        log.info("Kitchen order saved with id: {}", order.getId());

        notifyKitchenDisplay(order);
    }

    @Transactional
    public void updateOrder(OrderEvent event) {
        createOrUpdateOrder(event);
    }

    @Transactional
    public void addItemToOrder(OrderEvent event) {
        log.info("Adding items to kitchen order for check: {}", event.getCheckNumber());

        KitchenOrder order = orderRepository.findByCheckId(event.getCheckId())
                .orElseThrow(() -> new ResourceNotFoundException("KitchenOrder", "checkId", event.getCheckId()));

        for (OrderEvent.OrderItemEvent itemEvent : event.getItems()) {
            boolean exists = order.getItems().stream()
                    .anyMatch(i -> i.getCheckItemId().equals(itemEvent.getItemId()));

            if (!exists) {
                KitchenOrderItem item = KitchenOrderItem.builder()
                        .checkItemId(itemEvent.getItemId())
                        .menuItemId(itemEvent.getMenuItemId())
                        .itemName(itemEvent.getItemName())
                        .quantity(itemEvent.getQuantity())
                        .specialInstructions(itemEvent.getSpecialInstructions())
                        .modifiers(formatModifiers(itemEvent.getModifiers()))
                        .status(KitchenOrderItem.ItemStatus.PENDING)
                        .build();
                order.addItem(item);
            }
        }

        order = orderRepository.save(order);
        notifyKitchenDisplay(order);
    }

    @Transactional
    public void removeItemFromOrder(OrderEvent event) {
        log.info("Removing items from kitchen order for check: {}", event.getCheckNumber());

        KitchenOrder order = orderRepository.findByCheckId(event.getCheckId())
                .orElseThrow(() -> new ResourceNotFoundException("KitchenOrder", "checkId", event.getCheckId()));

        List<Long> itemIdsToRemove = event.getItems().stream()
                .map(OrderEvent.OrderItemEvent::getItemId)
                .collect(Collectors.toList());

        order.getItems().stream()
                .filter(item -> itemIdsToRemove.contains(item.getCheckItemId()))
                .forEach(item -> item.setStatus(KitchenOrderItem.ItemStatus.CANCELLED));

        order = orderRepository.save(order);
        notifyKitchenDisplay(order);
    }

    @Transactional
    public void modifyItem(OrderEvent event) {
        updateOrder(event);
    }

    @Transactional
    public void cancelOrder(Long checkId) {
        log.info("Cancelling kitchen order for check: {}", checkId);

        KitchenOrder order = orderRepository.findByCheckId(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenOrder", "checkId", checkId));

        order.setStatus(KitchenOrder.OrderStatus.CANCELLED);
        order.getItems().forEach(item -> item.setStatus(KitchenOrderItem.ItemStatus.CANCELLED));

        order = orderRepository.save(order);
        notifyKitchenDisplay(order);
    }

    public List<KitchenOrderDto> getActiveOrders(Long organizationId) {
        List<KitchenOrder.OrderStatus> activeStatuses = Arrays.asList(
                KitchenOrder.OrderStatus.RECEIVED,
                KitchenOrder.OrderStatus.IN_PREPARATION
        );

        return orderRepository.findActiveOrdersByOrganization(organizationId, activeStatuses)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public KitchenOrderDto getOrderById(Long id) {
        KitchenOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenOrder", "id", id));
        return toDto(order);
    }

    @Transactional
    public KitchenOrderDto updateOrderStatus(Long id, KitchenOrder.OrderStatus status) {
        log.info("Updating kitchen order {} status to {}", id, status);

        KitchenOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenOrder", "id", id));

        order.setStatus(status);

        if (status == KitchenOrder.OrderStatus.IN_PREPARATION && order.getStartedAt() == null) {
            order.setStartedAt(LocalDateTime.now());
        } else if (status == KitchenOrder.OrderStatus.READY || status == KitchenOrder.OrderStatus.SERVED) {
            order.setCompletedAt(LocalDateTime.now());
        }

        order = orderRepository.save(order);
        notifyKitchenDisplay(order);

        return toDto(order);
    }

    @Transactional
    public KitchenOrderDto updateItemStatus(Long orderId, Long itemId, KitchenOrderItem.ItemStatus status) {
        log.info("Updating kitchen order item {} status to {}", itemId, status);

        KitchenOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenOrder", "id", orderId));

        KitchenOrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("KitchenOrderItem", "id", itemId));

        item.setStatus(status);

        if (status == KitchenOrderItem.ItemStatus.IN_PREPARATION && item.getStartedAt() == null) {
            item.setStartedAt(LocalDateTime.now());
        } else if (status == KitchenOrderItem.ItemStatus.READY) {
            item.setCompletedAt(LocalDateTime.now());
        }

        // Auto-update order status based on items
        boolean allReady = order.getItems().stream()
                .filter(i -> i.getStatus() != KitchenOrderItem.ItemStatus.CANCELLED)
                .allMatch(i -> i.getStatus() == KitchenOrderItem.ItemStatus.READY);

        boolean anyInPrep = order.getItems().stream()
                .anyMatch(i -> i.getStatus() == KitchenOrderItem.ItemStatus.IN_PREPARATION);

        if (allReady) {
            order.setStatus(KitchenOrder.OrderStatus.READY);
            order.setCompletedAt(LocalDateTime.now());
        } else if (anyInPrep && order.getStatus() == KitchenOrder.OrderStatus.RECEIVED) {
            order.setStatus(KitchenOrder.OrderStatus.IN_PREPARATION);
            if (order.getStartedAt() == null) {
                order.setStartedAt(LocalDateTime.now());
            }
        }

        order = orderRepository.save(order);
        notifyKitchenDisplay(order);

        return toDto(order);
    }

    private void notifyKitchenDisplay(KitchenOrder order) {
        KitchenOrderDto dto = toDto(order);
        String destination = "/topic/kitchen/" + order.getOrganizationId();
        messagingTemplate.convertAndSend(destination, dto);
        log.debug("Sent WebSocket notification to {}", destination);
    }

    private String formatModifiers(List<OrderEvent.ModifierEvent> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return null;
        }
        return modifiers.stream()
                .map(OrderEvent.ModifierEvent::getModifierName)
                .collect(Collectors.joining(", "));
    }

    private KitchenOrderDto toDto(KitchenOrder order) {
        long elapsedSeconds = 0;
        if (order.getReceivedAt() != null) {
            LocalDateTime endTime = order.getCompletedAt() != null ?
                    order.getCompletedAt() : LocalDateTime.now();
            elapsedSeconds = Duration.between(order.getReceivedAt(), endTime).getSeconds();
        }

        return KitchenOrderDto.builder()
                .id(order.getId())
                .checkId(order.getCheckId())
                .checkNumber(order.getCheckNumber())
                .tableNumber(order.getTableNumber())
                .status(order.getStatus())
                .priority(order.getPriority())
                .receivedAt(order.getReceivedAt())
                .startedAt(order.getStartedAt())
                .completedAt(order.getCompletedAt())
                .organizationId(order.getOrganizationId())
                .terminalId(order.getTerminalId())
                .elapsedTimeSeconds(elapsedSeconds)
                .items(order.getItems().stream()
                        .map(this::toItemDto)
                        .collect(Collectors.toList()))
                .build();
    }

    private KitchenOrderItemDto toItemDto(KitchenOrderItem item) {
        return KitchenOrderItemDto.builder()
                .id(item.getId())
                .checkItemId(item.getCheckItemId())
                .menuItemId(item.getMenuItemId())
                .itemName(item.getItemName())
                .quantity(item.getQuantity())
                .specialInstructions(item.getSpecialInstructions())
                .modifiers(item.getModifiers())
                .status(item.getStatus())
                .startedAt(item.getStartedAt())
                .completedAt(item.getCompletedAt())
                .build();
    }
}
