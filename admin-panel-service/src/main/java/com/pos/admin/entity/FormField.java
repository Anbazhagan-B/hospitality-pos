package com.pos.admin.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "form_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_definition_id", nullable = false)
    private FormDefinition formDefinition;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FieldType fieldType;

    private String placeholder;

    private String defaultValue;

    private String helpText;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean readonly;

    @Column(nullable = false)
    private boolean hidden;

    private Integer displayOrder;

    private Integer minLength;

    private Integer maxLength;

    private String pattern;

    private String patternMessage;

    @Column(columnDefinition = "TEXT")
    private String options;

    private String dependsOn;

    private String dependsOnValue;

    @Column(columnDefinition = "TEXT")
    private String customValidation;

    public enum FieldType {
        TEXT,
        TEXTAREA,
        NUMBER,
        DECIMAL,
        EMAIL,
        PASSWORD,
        DATE,
        DATETIME,
        TIME,
        CHECKBOX,
        RADIO,
        SELECT,
        MULTISELECT,
        FILE,
        IMAGE,
        HIDDEN,
        CURRENCY
    }
}
