package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private CommitteeMeeting meeting;

    @Column(name = "has_decision", nullable = false)
    private Boolean hasDecision;

    @Column(name = "has_major_issue", nullable = false)
    private Boolean hasMajorIssue;

    @Column(name = "juwei_name", length = 50)
    private String juweiName;

    @Column(name = "juwei_signed", nullable = false)
    private Boolean juweiSigned;

    @Column(name = "minutes_text", columnDefinition = "TEXT")
    private String minutesText;

    /** 内部保存：详细 AI 议题报告，不作为公示纪要直接展示。 */
    @Column(name = "ai_topic_report_text", columnDefinition = "TEXT")
    private String aiTopicReportText;

    /** 面向执行跟踪：从会议内容抽取的待办事项清单。 */
    @Column(name = "todo_list_text", columnDefinition = "TEXT")
    private String todoListText;

    @Column(name = "quick_confirm_json", columnDefinition = "TEXT")
    private String quickConfirmJson;

    @Column(name = "quick_confirm_hash", length = 64)
    private String quickConfirmHash;

    @Column(name = "minutes_confirm_hash", length = 64)
    private String minutesConfirmHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
