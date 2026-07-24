package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 结构化待办事项。固化自 AI 从会议纪要抽取的待办文本（MeetingRecord.todoListText），
 * 落库后每条带独立 id 与可更新的状态，供委员点按钮回复进度。
 * 固化后以本表为准，不再被 AI 文本覆盖。
 */
@Entity
@Table(name = "meeting_todos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingTodo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属会议 id（按会议聚合，与 quick 待办口径一致）。 */
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /** 待办标题。 */
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    /** 负责人（AI 抽取的人名文本，可空，不强绑登录账号）。 */
    @Column(name = "owner", length = 100)
    private String owner;

    /** 截止时间原文（保留文本，不强制解析为日期）。 */
    @Column(name = "due_text", length = 100)
    private String dueText;

    /** 状态：todo=待处理/没空，doing=进行中，done=已完成。 */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 展示排序，固化时按解析顺序赋值。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    // 来源议题（0724 领导意见#4）：本待办派生自哪条议题的决议，仅存档追溯、前端不展示。
    // sourceRef=议题标题文本；sourceTopicId=匹配到的议题 id（匹配不到则空）。
    @Column(name = "source_ref", length = 300)
    private String sourceRef;

    @Column(name = "source_topic_id")
    private Long sourceTopicId;

    /** 最后更新状态的委员 userRole id，留痕。 */
    @Column(name = "last_actor_id")
    private Long lastActorId;

    /** 最后更新状态的委员姓名，留痕。 */
    @Column(name = "last_actor_name", length = 50)
    private String lastActorName;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 推送外部工单系统使用的稳定幂等号。 */
    @Column(name = "external_ticket_no", length = 128)
    private String externalTicketNo;

    /** 外部工单系统返回的正式工单号。 */
    @Column(name = "ticket_no", length = 128)
    private String ticketNo;

    @Column(name = "ticket_pushed_at")
    private LocalDateTime ticketPushedAt;
}
