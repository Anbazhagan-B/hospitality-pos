package com.pos.employee.service;

import com.pos.common.dto.PageResponse;
import com.pos.common.exception.BadRequestException;
import com.pos.common.exception.ResourceNotFoundException;
import com.pos.employee.dto.CreateEmployeeRequest;
import com.pos.employee.dto.EmployeeDto;
import com.pos.employee.dto.UpdateEmployeeRequest;
import com.pos.employee.entity.Employee;
import com.pos.employee.entity.Role;
import com.pos.employee.mapper.EmployeeMapper;
import com.pos.employee.repository.EmployeeRepository;
import com.pos.employee.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeMapper employeeMapper;

    @Transactional
    public EmployeeDto createEmployee(CreateEmployeeRequest request) {
        log.info("Creating new employee with username: {}", request.getUsername());

        if (employeeRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }

        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        Set<Role> roles = new HashSet<>();
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            roles = request.getRoleIds().stream()
                    .map(id -> roleRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id)))
                    .collect(Collectors.toSet());
        }

        Employee employee = Employee.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .organizationId(request.getOrganizationId())
                .roles(roles)
                .active(true)
                .build();

        employee = employeeRepository.save(employee);
        log.info("Employee created with id: {}", employee.getId());

        return employeeMapper.toDto(employee);
    }

    public EmployeeDto getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
        return employeeMapper.toDto(employee);
    }

    public EmployeeDto getEmployeeByUsername(String username) {
        Employee employee = employeeRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "username", username));
        return employeeMapper.toDto(employee);
    }

    public PageResponse<EmployeeDto> getAllEmployees(Pageable pageable) {
        Page<Employee> page = employeeRepository.findByActiveTrue(pageable);
        return toPageResponse(page);
    }

    public PageResponse<EmployeeDto> getEmployeesByOrganization(Long organizationId, Pageable pageable) {
        Page<Employee> page = employeeRepository.findByOrganizationId(organizationId, pageable);
        return toPageResponse(page);
    }

    @Transactional
    public EmployeeDto updateEmployee(Long id, UpdateEmployeeRequest request) {
        log.info("Updating employee with id: {}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));

        if (request.getFirstName() != null) {
            employee.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            employee.setLastName(request.getLastName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(employee.getEmail())) {
            if (employeeRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email already exists");
            }
            employee.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            employee.setPhone(request.getPhone());
        }
        if (request.getActive() != null) {
            employee.setActive(request.getActive());
        }
        if (request.getOrganizationId() != null) {
            employee.setOrganizationId(request.getOrganizationId());
        }
        if (request.getRoleIds() != null) {
            Set<Role> roles = request.getRoleIds().stream()
                    .map(roleId -> roleRepository.findById(roleId)
                            .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId)))
                    .collect(Collectors.toSet());
            employee.setRoles(roles);
        }

        employee = employeeRepository.save(employee);
        log.info("Employee updated with id: {}", id);

        return employeeMapper.toDto(employee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        log.info("Deleting employee with id: {}", id);

        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee", "id", id);
        }

        employeeRepository.deleteById(id);
        log.info("Employee deleted with id: {}", id);
    }

    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));

        if (!passwordEncoder.matches(oldPassword, employee.getPassword())) {
            throw new BadRequestException("Invalid old password");
        }

        employee.setPassword(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);
        log.info("Password changed for employee with id: {}", id);
    }

    private PageResponse<EmployeeDto> toPageResponse(Page<Employee> page) {
        return PageResponse.<EmployeeDto>builder()
                .content(page.getContent().stream()
                        .map(employeeMapper::toDto)
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
