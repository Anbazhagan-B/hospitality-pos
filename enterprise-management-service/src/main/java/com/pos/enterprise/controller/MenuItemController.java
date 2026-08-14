package com.pos.enterprise.controller;

import com.pos.common.dto.ApiResponse;
import com.pos.common.dto.PageResponse;
import com.pos.enterprise.dto.MenuItemDto;
import com.pos.enterprise.service.MenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
@Tag(name = "Menu Item Management", description = "Menu Item CRUD operations")
@SecurityRequirement(name = "bearerAuth")
public class MenuItemController {

    private final MenuItemService menuItemService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Create Menu Item", description = "Create a new menu item")
    public ResponseEntity<ApiResponse<MenuItemDto>> createMenuItem(
            @Valid @RequestBody MenuItemDto request) {
        MenuItemDto menuItem = menuItemService.createMenuItem(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(menuItem, "Menu item created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Menu Item by ID", description = "Retrieve a menu item by its ID")
    public ResponseEntity<ApiResponse<MenuItemDto>> getMenuItemById(@PathVariable Long id) {
        MenuItemDto menuItem = menuItemService.getMenuItemById(id);
        return ResponseEntity.ok(ApiResponse.success(menuItem));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get Menu Items by Organization", description = "Retrieve menu items by organization ID")
    public ResponseEntity<ApiResponse<PageResponse<MenuItemDto>>> getMenuItemsByOrganization(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<MenuItemDto> menuItems = menuItemService.getMenuItemsByOrganization(organizationId, pageable);
        return ResponseEntity.ok(ApiResponse.success(menuItems));
    }

    @GetMapping("/organization/{organizationId}/active")
    @Operation(summary = "Get Active Menu Items", description = "Retrieve all active menu items for an organization")
    public ResponseEntity<ApiResponse<List<MenuItemDto>>> getActiveMenuItems(
            @PathVariable Long organizationId) {
        List<MenuItemDto> menuItems = menuItemService.getActiveMenuItems(organizationId);
        return ResponseEntity.ok(ApiResponse.success(menuItems));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Update Menu Item", description = "Update an existing menu item")
    public ResponseEntity<ApiResponse<MenuItemDto>> updateMenuItem(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemDto request) {
        MenuItemDto menuItem = menuItemService.updateMenuItem(id, request);
        return ResponseEntity.ok(ApiResponse.success(menuItem, "Menu item updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Delete Menu Item", description = "Soft delete a menu item")
    public ResponseEntity<ApiResponse<Void>> deleteMenuItem(@PathVariable Long id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Menu item deleted successfully"));
    }
}
