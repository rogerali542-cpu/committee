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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
