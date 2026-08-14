package com.pos.employee.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEmployeeRequest {
    private String firstName;
    private String lastName;

    @Email(message = "Email must be valid")
    private String email;

    private String phone;
    private Boolean active;
    private Long organizationId;
    private Set<Long> roleIds;
}
