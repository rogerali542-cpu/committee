package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 会议纪要生成任务：把"生成中/已完成/失败"落库成服务端可查的状态。
 * 目的：前端后台任务原为纯内存单例，整页刷新/换设备就丢、重进会议没入口。
 * 有了这张表，重进时可调 /quick/minutes-status 拿服务端权威状态，跨刷新/换设备/隔天都能接上。
 */
@Entity
@Table(name = "minutes_tasks", indexes = {
        @Index(name = "idx_minutes_task_meeting", columnList = "meeting_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinutesTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /** running / success / failed */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_msg", length = 512)
    private String errorMsg;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
