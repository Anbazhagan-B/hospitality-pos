package com.pos.enterprise.controller;

import com.pos.common.dto.ApiResponse;
import com.pos.common.dto.PageResponse;
import com.pos.enterprise.dto.CreateOrganizationRequest;
import com.pos.enterprise.dto.OrganizationDto;
import com.pos.enterprise.service.OrganizationService;
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

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Tag(name = "Organization Management", description = "Organization CRUD operations")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create Organization", description = "Create a new organization")
    public ResponseEntity<ApiResponse<OrganizationDto>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationDto organization = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(organization, "Organization created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Organization by ID", description = "Retrieve an organization by its ID")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganizationById(@PathVariable Long id) {
        OrganizationDto organization = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(ApiResponse.success(organization));
    }

    @GetMapping
    @Operation(summary = "Get All Organizations", description = "Retrieve all organizations with pagination")
    public ResponseEntity<ApiResponse<PageResponse<OrganizationDto>>> getAllOrganizations(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<OrganizationDto> organizations = organizationService.getAllOrganizations(pageable);
        return ResponseEntity.ok(ApiResponse.success(organizations));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update Organization", description = "Update an existing organization")
    public ResponseEntity<ApiResponse<OrganizationDto>> updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationDto organization = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(ApiResponse.success(organization, "Organization updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete Organization", description = "Soft delete an organization")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Organization deleted successfully"));
    }
}
