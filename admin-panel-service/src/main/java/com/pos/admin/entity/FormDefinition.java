package com.pos.admin.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "form_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormDefinition extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String displayName;

    private String description;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private boolean active = true;

    private Integer displayOrder;

    @OneToMany(mappedBy = "formDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<FormField> fields = new ArrayList<>();

    @Column(name = "organization_id")
    private Long organizationId;

    public void addField(FormField field) {
        fields.add(field);
        field.setFormDefinition(this);
    }

    public void removeField(FormField field) {
        fields.remove(field);
        field.setFormDefinition(null);
    }
}
