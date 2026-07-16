package com.ywh.entity;

import com.ywh.enums.ReceptionCategory;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "reception_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceptionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    private LocalDate date;

    private LocalTime time;

    @Column(name = "visitor_name", length = 30)
    private String visitorName;

    @Column(length = 50)
    private String room;

    @Column(length = 30)
    private String receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ReceptionCategory category;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "fed_property", nullable = false)
    private Boolean fedProperty;

    @Column(name = "fed_owner", nullable = false)
    private Boolean fedOwner;

    // 物业处理工单（仅物业类）：pending_dispatch / dispatched / replied
    @Column(name = "property_status", length = 20)
    private String propertyStatus;

    @Column(name = "property_reply", columnDefinition = "TEXT")
    private String propertyReply;

    @Column(name = "property_replied_by", length = 30)
    private String propertyRepliedBy;

    @Column(name = "property_replied_at")
    private LocalDateTime propertyRepliedAt;

    // 业委会向业主反馈诉求解决情况：真闭环——留正文+反馈人+时间，替代原 fedOwner 空开关
    @Column(name = "owner_feedback", columnDefinition = "TEXT")
    private String ownerFeedback;

    @Column(name = "owner_fed_by", length = 30)
    private String ownerFedBy;

    @Column(name = "owner_fed_at")
    private LocalDateTime ownerFedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
