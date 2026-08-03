package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_notification_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingNotificationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private CommitteeMeeting meeting;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "sent_by_name", length = 60)
    private String sentByName;

    @Column(name = "channel", length = 20)
    private String channel;

    // 本次送达人数（0801，可空：微信留痕没有人数，旧数据也没有；ddl-auto update 自动加列）
    // 前端通知记录行要写「已通知 7 人」，人数必须在发送当时定格——delivery 表是"当前"状态，清空/重发后就对不上历史
    @Column(name = "sent_count")
    private Integer sentCount;
}
