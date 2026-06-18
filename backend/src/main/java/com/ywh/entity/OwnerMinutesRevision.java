package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 业主大会纪要修订版本快照（见 产品边界定稿.md §5）。
 * 与 {@link MinutesRevision} 同构，但归属业主大会（owner_meetings）。
 */
@Entity
@Table(name = "owner_minutes_revisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerMinutesRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "editor_id")
    private Long editorId;

    @Column(name = "editor_name", length = 50)
    private String editorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
