package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "housing_units")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HousingUnit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private String building;

    @Column(name = "unit_no", nullable = false)
    private String unitNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal area;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "owner_name")
    private String ownerName;

    /** 投票代表人 user_role_id（每户唯一，非代表人不可投票） */
    @Column(name = "representative_user_id")
    private Long representativeUserId;

    private String phone;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
