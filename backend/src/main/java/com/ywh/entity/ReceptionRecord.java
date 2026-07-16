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

    // ⚠ 废弃字段，恒 false，业务逻辑不再读它俩（0716 内部派单流下线）。
    // 之所以不删：这两列在库里是 NOT NULL 无默认值，而本项目 Flyway 是关的、靠 ddl-auto:update 维护 schema，
    // 而 update 只加列不删列。删掉字段 → 列还在且 NOT NULL → 新增接待记录直接 SQL 报错。
    // 要真正清掉，得手工 ALTER TABLE 后再删这两行。
    @Column(name = "fed_property", nullable = false)
    private Boolean fedProperty;

    @Column(name = "fed_owner", nullable = false)
    private Boolean fedOwner;

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
