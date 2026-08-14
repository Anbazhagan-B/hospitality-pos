package com.pos.admin.dto;

import com.pos.admin.entity.FormField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormFieldDto {
    private Long id;
    private String name;
    private String label;
    private FormField.FieldType fieldType;
    private String placeholder;
    private String defaultValue;
    private String helpText;
    private boolean required;
    private boolean readonly;
    private boolean hidden;
    private Integer displayOrder;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private String patternMessage;
    private String options;
    private String dependsOn;
    private String dependsOnValue;
    private String customValidation;
}
