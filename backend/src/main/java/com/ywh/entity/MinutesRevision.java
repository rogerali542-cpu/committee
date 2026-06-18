package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 会议纪要修订版本快照（见 产品边界定稿.md §5）。
 * 每次纪要文本被保存时追加一条，留存编辑人与时间，不可篡改、不丢历史。
 */
@Entity
@Table(name = "minutes_revisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinutesRevision {
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
