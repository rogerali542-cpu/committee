package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "housing_unit_owners")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HousingUnitOwner {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "user_role_id", nullable = false)
    private Long userRoleId;
}
