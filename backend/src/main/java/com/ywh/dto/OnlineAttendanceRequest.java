package com.ywh.dto;

import lombok.Data;
import java.util.List;

@Data
public class OnlineAttendanceRequest {
    private List<Long> presentMemberIds;
}
