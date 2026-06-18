package com.ywh.entity;

import com.ywh.enums.ComplianceStatus;
import com.ywh.enums.MeetingMode;
import com.ywh.enums.MeetingStage;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "owner_meetings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerMeeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "meeting_date")
    private LocalDate meetingDate;

    @Column(name = "meeting_time")
    private LocalTime meetingTime;

    @Column(length = 100)
    private String location;

    @Column(length = 10)
    private String type; // regular, special

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MeetingStage stage;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ComplianceStatus compliance;

    // 会议模式：普通 / 快速会议（开始大会时确定）
    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_mode", length = 10)
    private MeetingMode meetingMode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_owners")
    private Integer totalOwners;

    @Column(name = "total_area")
    private Integer totalArea;

    @Column(name = "needs_vote")
    private Boolean needsVote;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
