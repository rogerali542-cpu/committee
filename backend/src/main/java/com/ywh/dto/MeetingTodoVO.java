package com.ywh.dto;

import lombok.Data;

/**
 * 结构化待办的传输对象。既用于固化入参（前端把解析出的待办数组传来），
 * 也用于列表/更新返回。入参只读 title/owner/dueText/status；返回额外带 id、留痕信息。
 */
@Data
public class MeetingTodoVO {
    private Long id;
    private String title;
    private String owner;
    private String dueText;
    /** todo=待处理/没空，doing=进行中，done=已完成。 */
    private String status;
    /** 来源议题（0724）：固化入参携带，标明本待办出自哪条议题；仅存档追溯，界面不展示。 */
    private String sourceRef;
    /** 最后更新状态的委员姓名（留痕展示）。 */
    private String lastActorName;
    /** 最后更新时间，已格式化为 MM-dd HH:mm。 */
    private String updatedAt;
    private String externalTicketNo;
    private String ticketNo;
    private String ticketPushedAt;
    /** 来源会议（0730 独立待办页）：仅跨会议聚合接口填充；单会议接口下为 null。 */
    private Long meetingId;
    private String meetingTitle;
    private String meetingDate;
}
