package com.pos.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormDefinitionDto {
    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String entityType;
    private boolean active;
    private Integer displayOrder;
    private Long organizationId;
    private List<FormFieldDto> fields;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
