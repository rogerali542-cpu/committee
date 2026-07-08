package com.ywh.dto.quick;

import lombok.*;

/** 纪要生成任务状态：前端重进会议时查它来决定显示「生成中/查看/可重新生成」。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinutesTaskStatusVO {
    /** none=从没生成过 / running=生成中 / success=已完成 / failed=失败 */
    private String status;
    private Long taskId;
}
