package com.ywh.dto.quick;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSummaryTaskVO {
    private String taskId;
    private Long meetingId;
    private String status; // queued | running | succeeded | failed
    private String title;
    private String type;
    private String result;
    private String message;
    private Long createdAt;
    private Long updatedAt;
}
