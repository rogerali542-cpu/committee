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

    @Column(name = "quick_confirm_json", columnDefinition = "TEXT")
    private String quickConfirmJson;

    @Column(name = "quick_confirm_hash", length = 64)
    private String quickConfirmHash;

    @Column(name = "minutes_confirm_hash", length = 64)
    private String minutesConfirmHash;

    /** 进行中"录音负责人"：任意已签到参会人可认领/转交（advisory 协调）。null=暂无人负责。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recorder_role_id")
    private UserRoleEntity recorder;

    /** 会议录音存档地址（上传后落库，会后可回放/下载）。重录覆盖为最新。null=无录音。 */
    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
