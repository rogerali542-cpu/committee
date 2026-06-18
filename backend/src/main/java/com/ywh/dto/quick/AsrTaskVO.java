package com.ywh.dto.quick;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步 ASR 任务状态。豆包转写是异步的，前端拿 taskId 轮询 status。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrTaskVO {
    private String taskId;
    private Long meetingId;
    private String status;   // pending | processing | done | failed
    private String message;  // 失败原因等
}
