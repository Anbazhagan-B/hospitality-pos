package com.pos.admin.controller;

import com.pos.admin.dto.FormDefinitionDto;
import com.pos.admin.service.FormDefinitionService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
@Tag(name = "Form Definition Management", description = "Admin panel form definition operations")
@SecurityRequirement(name = "bearerAuth")
public class FormDefinitionController {

    private final FormDefinitionService formDefinitionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create Form Definition", description = "Create a new form definition")
    public ResponseEntity<ApiResponse<FormDefinitionDto>> createFormDefinition(
            @Valid @RequestBody FormDefinitionDto dto) {
        FormDefinitionDto formDefinition = formDefinitionService.createFormDefinition(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(formDefinition, "Form definition created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Form Definition by ID", description = "Retrieve a form definition by its ID")
    public ResponseEntity<ApiResponse<FormDefinitionDto>> getFormDefinitionById(@PathVariable Long id) {
        FormDefinitionDto formDefinition = formDefinitionService.getFormDefinitionById(id);
        return ResponseEntity.ok(ApiResponse.success(formDefinition));
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "Get Form Definition by Name", description = "Retrieve a form definition by its name")
    public ResponseEntity<ApiResponse<FormDefinitionDto>> getFormDefinitionByName(@PathVariable String name) {
        FormDefinitionDto formDefinition = formDefinitionService.getFormDefinitionByName(name);
        return ResponseEntity.ok(ApiResponse.success(formDefinition));
    }

    @GetMapping("/entity/{entityType}")
    @Operation(summary = "Get Form Definition by Entity Type", description = "Retrieve a form definition by entity type")
    public ResponseEntity<ApiResponse<FormDefinitionDto>> getFormDefinitionByEntityType(
            @PathVariable String entityType) {
        FormDefinitionDto formDefinition = formDefinitionService.getFormDefinitionByEntityType(entityType);
        return ResponseEntity.ok(ApiResponse.success(formDefinition));
    }

    @GetMapping
    @Operation(summary = "Get All Form Definitions", description = "Retrieve all form definitions with pagination")
    public ResponseEntity<ApiResponse<PageResponse<FormDefinitionDto>>> getAllFormDefinitions(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<FormDefinitionDto> formDefinitions = formDefinitionService.getAllFormDefinitions(pageable);
        return ResponseEntity.ok(ApiResponse.success(formDefinitions));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get Form Definitions by Organization", description = "Retrieve form definitions by organization ID")
    public ResponseEntity<ApiResponse<List<FormDefinitionDto>>> getFormDefinitionsByOrganization(
            @PathVariable Long organizationId) {
        List<FormDefinitionDto> formDefinitions = formDefinitionService.getFormDefinitionsByOrganization(organizationId);
        return ResponseEntity.ok(ApiResponse.success(formDefinitions));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update Form Definition", description = "Update an existing form definition")
    public ResponseEntity<ApiResponse<FormDefinitionDto>> updateFormDefinition(
            @PathVariable Long id,
            @Valid @RequestBody FormDefinitionDto dto) {
        FormDefinitionDto formDefinition = formDefinitionService.updateFormDefinition(id, dto);
        return ResponseEntity.ok(ApiResponse.success(formDefinition, "Form definition updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete Form Definition", description = "Soft delete a form definition")
    public ResponseEntity<ApiResponse<Void>> deleteFormDefinition(@PathVariable Long id) {
        formDefinitionService.deleteFormDefinition(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Form definition deleted successfully"));
    }
}
