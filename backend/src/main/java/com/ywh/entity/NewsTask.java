package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 党建新闻生成任务：把"生成中/已完成/失败" + 生成结果（标题/正文）落库。
 * 目的：新闻生成原为同步请求、结果只存前端内存，退出微信/整页刷新即丢、切回没入口。
 * 有了这张表，生成走 @Async 后台线程跑到底并存库；前端切回调 /news-status 拿服务端权威状态与结果，
 * 退微信/锁屏/换设备/隔天都能接上「查看新闻稿」。仿 {@link MinutesTask}，但额外存标题/正文。
 */
@Entity
@Table(name = "news_tasks", indexes = {
        @Index(name = "idx_news_task_meeting", columnList = "meeting_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /** running / success / failed */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "title", length = 512)
    private String title;

    /** 新闻正文，可较长 → LONGTEXT。 */
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

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
