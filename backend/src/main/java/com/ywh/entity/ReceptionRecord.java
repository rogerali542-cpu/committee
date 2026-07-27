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

    /** 同一次接待可能登记多位居民；同一场次的事项共享此键。 */
    @Column(name = "session_key", length = 64)
    private String sessionKey;

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

    // ── 外部工单（0716）：派给「社区智能运维协同平台」后回填，照 MeetingTodo 的同名三字段 ──
    // externalTicketNo 由本系统生成（YWH-RECEPTION-{id}）并用于幂等；ticketNo 是对方系统的单号。
    @Column(name = "external_ticket_no", length = 128)
    private String externalTicketNo;

    @Column(name = "ticket_no", length = 128)
    private String ticketNo;

    @Column(name = "ticket_pushed_at")
    private LocalDateTime ticketPushedAt;

    // ── 转物业（0717）：跟上面的工单是两条不同的路 ──
    // 工单那条真的 POST 到外部工单系统、有对方单号、不可撤销；这条不发任何请求，
    // 只是委员自己联系了物业、在本系统记一笔，所以可以随手反悔（前端是个开关）。
    // 时间戳兼作布尔：null = 没转过。⚠ 不参与 isDone —— 转出去 ≠ 办结。
    @Column(name = "property_transferred_at")
    private LocalDateTime propertyTransferredAt;

    // 办结时间（0727）：填写处理结果=办结时记一次。有来访的接待办结满一个月后，
    // 由「公示中」转「已留档」；无人来访登记即办结（直接留档）。⚠ 不参与 isDone。
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
