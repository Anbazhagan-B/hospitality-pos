package com.pos.check.controller;

import com.pos.check.dto.AddItemRequest;
import com.pos.check.dto.CheckDto;
import com.pos.check.dto.CreateCheckRequest;
import com.pos.check.service.CheckService;
import com.pos.common.dto.ApiResponse;
import com.pos.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checks")
@RequiredArgsConstructor
@Tag(name = "Check Management", description = "Check and order operations")
@SecurityRequirement(name = "bearerAuth")
public class CheckController {

    private final CheckService checkService;

    @PostMapping
    @Operation(summary = "Create Check", description = "Create a new check")
    public ResponseEntity<ApiResponse<CheckDto>> createCheck(
            @Valid @RequestBody CreateCheckRequest request) {
        CheckDto check = checkService.createCheck(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(check, "Check created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Check by ID", description = "Retrieve a check by its ID")
    public ResponseEntity<ApiResponse<CheckDto>> getCheckById(@PathVariable Long id) {
        CheckDto check = checkService.getCheckById(id);
        return ResponseEntity.ok(ApiResponse.success(check));
    }

    @GetMapping("/number/{checkNumber}")
    @Operation(summary = "Get Check by Number", description = "Retrieve a check by its check number")
    public ResponseEntity<ApiResponse<CheckDto>> getCheckByNumber(@PathVariable String checkNumber) {
        CheckDto check = checkService.getCheckByNumber(checkNumber);
        return ResponseEntity.ok(ApiResponse.success(check));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get Checks by Organization", description = "Retrieve checks by organization ID")
    public ResponseEntity<ApiResponse<PageResponse<CheckDto>>> getChecksByOrganization(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<CheckDto> checks = checkService.getChecksByOrganization(organizationId, pageable);
        return ResponseEntity.ok(ApiResponse.success(checks));
    }

    @GetMapping("/organization/{organizationId}/open")
    @Operation(summary = "Get Open Checks", description = "Retrieve all open checks for an organization")
    public ResponseEntity<ApiResponse<List<CheckDto>>> getOpenChecks(
            @PathVariable Long organizationId) {
        List<CheckDto> checks = checkService.getOpenChecks(organizationId);
        return ResponseEntity.ok(ApiResponse.success(checks));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Add Item to Check", description = "Add an item to an existing check")
    public ResponseEntity<ApiResponse<CheckDto>> addItem(
            @PathVariable Long id,
            @Valid @RequestBody AddItemRequest request) {
        CheckDto check = checkService.addItem(id, request);
        return ResponseEntity.ok(ApiResponse.success(check, "Item added successfully"));
    }

    @PostMapping("/{id}/send-to-kitchen")
    @Operation(summary = "Send to Kitchen", description = "Send ordered items to kitchen")
    public ResponseEntity<ApiResponse<CheckDto>> sendToKitchen(@PathVariable Long id) {
        CheckDto check = checkService.sendToKitchen(id);
        return ResponseEntity.ok(ApiResponse.success(check, "Order sent to kitchen"));
    }

    @PostMapping("/{checkId}/items/{itemId}/void")
    @Operation(summary = "Void Item", description = "Void an item on a check")
    public ResponseEntity<ApiResponse<CheckDto>> voidItem(
            @PathVariable Long checkId,
            @PathVariable Long itemId,
            @RequestParam String reason) {
        CheckDto check = checkService.voidItem(checkId, itemId, reason);
        return ResponseEntity.ok(ApiResponse.success(check, "Item voided successfully"));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close Check", description = "Close a fully paid check")
    public ResponseEntity<ApiResponse<CheckDto>> closeCheck(@PathVariable Long id) {
        CheckDto check = checkService.closeCheck(id);
        return ResponseEntity.ok(ApiResponse.success(check, "Check closed successfully"));
    }
}
