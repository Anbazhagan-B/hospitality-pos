package com.pos.employee.controller;

import com.pos.common.dto.ApiResponse;
import com.pos.common.dto.PageResponse;
import com.pos.employee.dto.CreateEmployeeRequest;
import com.pos.employee.dto.EmployeeDto;
import com.pos.employee.dto.UpdateEmployeeRequest;
import com.pos.employee.service.EmployeeService;
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
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Tag(name = "Employee Management", description = "Employee CRUD operations")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Create Employee", description = "Create a new employee")
    public ResponseEntity<ApiResponse<EmployeeDto>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeDto employee = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(employee, "Employee created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Employee by ID", description = "Retrieve an employee by their ID")
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployeeById(@PathVariable Long id) {
        EmployeeDto employee = employeeService.getEmployeeById(id);
        return ResponseEntity.ok(ApiResponse.success(employee));
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Get Employee by Username", description = "Retrieve an employee by their username")
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployeeByUsername(@PathVariable String username) {
        EmployeeDto employee = employeeService.getEmployeeByUsername(username);
        return ResponseEntity.ok(ApiResponse.success(employee));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Get All Employees", description = "Retrieve all employees with pagination")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeDto>>> getAllEmployees(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<EmployeeDto> employees = employeeService.getAllEmployees(pageable);
        return ResponseEntity.ok(ApiResponse.success(employees));
    }

    @GetMapping("/organization/{organizationId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Get Employees by Organization", description = "Retrieve employees by organization ID")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeDto>>> getEmployeesByOrganization(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<EmployeeDto> employees = employeeService.getEmployeesByOrganization(organizationId, pageable);
        return ResponseEntity.ok(ApiResponse.success(employees));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Update Employee", description = "Update an existing employee")
    public ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        EmployeeDto employee = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(ApiResponse.success(employee, "Employee updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete Employee", description = "Delete an employee")
    public ResponseEntity<ApiResponse<Void>> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Employee deleted successfully"));
    }

    @PostMapping("/{id}/change-password")
    @Operation(summary = "Change Password", description = "Change employee password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long id,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        employeeService.changePassword(id, oldPassword, newPassword);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}
