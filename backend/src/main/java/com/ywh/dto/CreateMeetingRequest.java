package com.ywh.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import com.ywh.enums.MeetingMethod;

@Data
public class CreateMeetingRequest {
    @NotBlank
    private String title;
    private LocalDate meetingDate;
    private LocalTime meetingTime;
    private String location;
    private MeetingMethod meetingMethod;
    private String description;
    private List<TopicRequest> topics;

    @Data
    public static class TopicRequest {
        private String title;
        private String type;
        private String decisionType;
        private List<Map<String, Object>> options;
        private Boolean realNameVote;
        private String content;   // 通报类议题正文
    }
}
