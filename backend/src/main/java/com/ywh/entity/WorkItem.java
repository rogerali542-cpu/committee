package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 跨业务事项的基础模型。
 * 当前只提供独立存储与基础接口，尚未绑定接待、会议、待办、公示或演示数据。
 */
@Entity
@Table(name = "work_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "owner_name", length = 100)
    private String ownerName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
