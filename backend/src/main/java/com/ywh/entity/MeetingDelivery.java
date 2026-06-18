package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_deliveries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"meeting_id", "user_role_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private CommitteeMeeting meeting;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role_id", nullable = false)
    private UserRoleEntity userRole;

    @Column(name = "notice_delivered", nullable = false)
    private Boolean noticeDelivered;

    @Column(name = "material_delivered", nullable = false)
    private Boolean materialDelivered;

    /** 委员首次查看会议详情（已送达的通知）时间。null=未读。送达由主任置位，已读由委员触发。 */
    @Column(name = "notice_read_at")
    private LocalDateTime noticeReadAt;

    /** 委员首次查看已送达材料的时间。null=未读。 */
    @Column(name = "material_read_at")
    private LocalDateTime materialReadAt;
}
