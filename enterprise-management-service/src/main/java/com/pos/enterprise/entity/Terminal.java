package com.pos.enterprise.entity;

import com.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "terminals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Terminal extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String terminalId;

    @Column(nullable = false)
    private String name;

    private String description;

    private String location;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profit_center_id")
    private ProfitCenter profitCenter;
}
