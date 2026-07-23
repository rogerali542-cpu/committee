package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "communities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Community {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String address;

    /** 业委会届别（真实材料：备案证/公章/落款均带「第X届」）。空按「第一届」处理。 */
    @Column(name = "committee_term", length = 20)
    private String committeeTerm;

    /** 行政区划前缀（市+区），拼正式落款全称用，如「上海市静安区」+小区名+业主委员会（第X届）。 */
    @Column(name = "org_region", length = 60)
    private String orgRegion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
