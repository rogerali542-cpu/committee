package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 小区公告：由业委会（主任/副主任）发布，在「小区首页 - 小区公告」卡片展示。
 */
@Entity
@Table(name = "notices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** 公告日期（展示用） */
    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail;

    /** 是否对居民公开展示 */
    @Column(nullable = false)
    private Boolean published;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.published == null) {
            this.published = true;
        }
    }
}
