package com.ywh.entity;

import com.ywh.enums.TopicType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "record_topics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordTopic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private MeetingRecord record;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TopicType type;

    @Column(name = "decision_type", nullable = false, length = 15)
    private String decisionType = "simple";

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "sort_order")
    private Integer sortOrder;

    // ===== 留痕（规则6）：来源 + 添加人 =====
    @Column(nullable = false, length = 20)
    private String source = "live";   // live=现场新增

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name", length = 50)
    private String createdByName;

    // ===== 实名表决标记（规则5）：true 时个人投票可公开 =====
    @Column(name = "real_name_vote", nullable = false)
    private Boolean realNameVote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
