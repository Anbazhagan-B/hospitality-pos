package com.pos.employee.mapper;

import com.pos.employee.dto.EmployeeDto;
import com.pos.employee.dto.PermissionDto;
import com.pos.employee.dto.RoleDto;
import com.pos.employee.entity.Employee;
import com.pos.employee.entity.Permission;
import com.pos.employee.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    EmployeeDto toDto(Employee employee);

    RoleDto toDto(Role role);

    PermissionDto toDto(Permission permission);

    void updateEmployeeFromDto(EmployeeDto dto, @MappingTarget Employee employee);
}
