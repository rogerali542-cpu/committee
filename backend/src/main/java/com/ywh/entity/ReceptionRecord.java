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

    // ── 外部工单（0716）：派给「社区智能运维协同平台」后回填，照 MeetingTodo 的同名三字段 ──
    // externalTicketNo 由本系统生成（YWH-RECEPTION-{id}）并用于幂等；ticketNo 是对方系统的单号。
    @Column(name = "external_ticket_no", length = 128)
    private String externalTicketNo;

    @Column(name = "ticket_no", length = 128)
    private String ticketNo;

    @Column(name = "ticket_pushed_at")
    private LocalDateTime ticketPushedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
