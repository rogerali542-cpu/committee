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
    private String minutesMarkdown;   // 正式/公示会议纪要：简洁、正式
    private String topicReportMarkdown; // 内部 AI 议题报告：详细保存
    private String todoListMarkdown;  // 待办事项清单：结构化展示/跟踪
    private Boolean fallbackUsed;      // true=大模型失败或未启用，使用规则兜底
    private String errorCode;          // LLM_AUTH_FAILED / LLM_TIMEOUT / LLM_PARSE_FAILED 等
    private String errorMessage;       // 面向前端展示的简要原因
    private String source;             // llm / fallback

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicSummary {
        private String ref;          // 对应 topicId 或 tempId
        private String summary;      // 议题讨论摘要
        private String resolution;   // 决议结论
        private List<String> todos;  // 待办清单
        private List<OpinionDraft> opinions; // AI 从现场发言提炼的委员意见（入库带"现场·AI"标，可认领）
    }

    /** 现场发言提炼出的单条意见。speaker 为转写中的发言人（名字或 S1 等编号，归属不明时由入库侧标"待认领"）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpinionDraft {
        private String speaker;
        private String text;
    }
}
