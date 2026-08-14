package com.pos.enterprise.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrganizationRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private String address;
    private String phone;

    @Email(message = "Email must be valid")
    private String email;
}
