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
    private Double locationLat;  // 地图选点经纬度（0723，可空）：导航用精确坐标
    private Double locationLng;
    // 本次更新是否要连坐标一起写（0801）：更新接口对坐标默认"非空才覆盖"，无法表达"清空"——
    // 从地图选点改回手填/常用地点时，前端传的 null 会被忽略，库里留着旧坐标，导航仍指原地点。
    // 置 true 表示 locationLat/Lng 按原样写入（含 null）。不传或 false 维持原有非空才覆盖的行为。
    private Boolean updateLocationCoords;
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
