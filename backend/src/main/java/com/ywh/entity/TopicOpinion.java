package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 议题意见：委员对某议题发表的意见。
 * 来源三种：text=app打字、voice=app语音转文字、ai=会场录音AI提炼。
 * ai 来源可能无法确定发言人：userRole 为空、speakerName 存"现场发言"，待本人/主任认领（P3）。
 */
@Entity
@Table(name = "topic_opinions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicOpinion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private RecordTopic topic;

    // 可空：AI 提炼且未识别发言人时为 null，认领后回填
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role_id")
    private UserRoleEntity userRole;

    // 展示名快照；userRole 为空时显示它（如"现场发言"）
    @Column(name = "speaker_name", length = 50)
    private String speakerName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String source = "text";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
