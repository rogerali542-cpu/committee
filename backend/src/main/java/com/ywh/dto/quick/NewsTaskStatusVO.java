package com.ywh.dto.quick;

import lombok.*;

/**
 * 党建新闻生成任务状态：前端切回页面时查它来决定显示「生成中 / 查看新闻稿 / 可重新生成」。
 * success 时直接带回标题+正文，切回页面无需再生成即可查看（即便前端缓存已丢）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsTaskStatusVO {
    /** none=从没生成过 / running=生成中 / success=已完成 / failed=失败 */
    private String status;
    private Long taskId;
    private String title;
    private String content;
}
