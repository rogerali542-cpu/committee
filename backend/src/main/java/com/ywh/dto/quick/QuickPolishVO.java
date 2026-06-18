package com.ywh.dto.quick;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 大模型整理产物（按需）。把规则层结果 + 转写口语 → 书面纪要。
 * 由豆包大模型生成（见 MinutesGenService 桩）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickPolishVO {
    private Long meetingId;
    private List<TopicSummary> topics;
    private String minutesMarkdown;   // 可直接回填 committeeUpdateMinutes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicSummary {
        private String ref;          // 对应 topicId 或 tempId
        private String summary;      // 议题讨论摘要
        private String resolution;   // 决议结论
        private List<String> todos;  // 待办清单
    }
}
