package com.pos.enterprise.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "menu_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuCategory extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    private Integer displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MenuCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<MenuCategory> subCategories = new ArrayList<>();

    @OneToMany(mappedBy = "category")
    @Builder.Default
    private List<MenuItem> items = new ArrayList<>();

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
}
