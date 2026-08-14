package com.pos.kitchen.controller;

import com.pos.common.dto.ApiResponse;
import com.pos.kitchen.dto.KitchenOrderDto;
import com.pos.kitchen.entity.KitchenOrder;
import com.pos.kitchen.entity.KitchenOrderItem;
import com.pos.kitchen.service.KitchenOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kitchen/orders")
@RequiredArgsConstructor
@Tag(name = "Kitchen Display", description = "Kitchen order display and management")
@SecurityRequirement(name = "bearerAuth")
public class KitchenOrderController {

    private final KitchenOrderService kitchenOrderService;

    @GetMapping("/organization/{organizationId}/active")
    @Operation(summary = "Get Active Orders", description = "Retrieve all active kitchen orders for an organization")
    public ResponseEntity<ApiResponse<List<KitchenOrderDto>>> getActiveOrders(
            @PathVariable Long organizationId) {
        List<KitchenOrderDto> orders = kitchenOrderService.getActiveOrders(organizationId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Order by ID", description = "Retrieve a kitchen order by its ID")
    public ResponseEntity<ApiResponse<KitchenOrderDto>> getOrderById(@PathVariable Long id) {
        KitchenOrderDto order = kitchenOrderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update Order Status", description = "Update the status of a kitchen order")
    public ResponseEntity<ApiResponse<KitchenOrderDto>> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam KitchenOrder.OrderStatus status) {
        KitchenOrderDto order = kitchenOrderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(order, "Order status updated"));
    }

    @PatchMapping("/{orderId}/items/{itemId}/status")
    @Operation(summary = "Update Item Status", description = "Update the status of a kitchen order item")
    public ResponseEntity<ApiResponse<KitchenOrderDto>> updateItemStatus(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam KitchenOrderItem.ItemStatus status) {
        KitchenOrderDto order = kitchenOrderService.updateItemStatus(orderId, itemId, status);
        return ResponseEntity.ok(ApiResponse.success(order, "Item status updated"));
    }

    @PostMapping("/{id}/bump")
    @Operation(summary = "Bump Order", description = "Mark an order as ready/served")
    public ResponseEntity<ApiResponse<KitchenOrderDto>> bumpOrder(@PathVariable Long id) {
        KitchenOrderDto order = kitchenOrderService.updateOrderStatus(id, KitchenOrder.OrderStatus.READY);
        return ResponseEntity.ok(ApiResponse.success(order, "Order bumped"));
    }
}
