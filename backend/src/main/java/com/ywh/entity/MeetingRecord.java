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

    /** 列席人员（居委/街道/物业等非委员到会者，顿号分隔）。真实记录实到写「7+3」=委员+列席。 */
    @Column(name = "observers_text", length = 500)
    private String observersText;

    @Column(name = "quick_confirm_json", columnDefinition = "TEXT")
    private String quickConfirmJson;

    /** 议题结果人工改动留痕（JSON 数组：topicId/from/to/byName/at/text）。主任/秘书改结果必须留记录。 */
    @Column(name = "result_audit_json", columnDefinition = "TEXT")
    private String resultAuditJson;

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
