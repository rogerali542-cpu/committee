package com.ywh.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeetingTodoTicketVO {
    private boolean created;
    private String externalTicketNo;
    private String ticketNo;
    private Long ticketId;
    private String status;
    private String statusLabel;
    private String pushedAt;
}
