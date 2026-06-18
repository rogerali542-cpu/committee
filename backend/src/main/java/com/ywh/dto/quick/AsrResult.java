package com.ywh.dto.quick;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 语音识别(豆包 ASR)产物：整段会议的结构化转写。
 * 文本 + 说话人(分离) + 时间戳。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrResult {
    private Long meetingId;
    private int durationSec;
    private List<Segment> segments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        private String speaker;   // 说话人标识，如 S1 / S2（豆包说话人分离给出）
        private long startMs;
        private long endMs;
        private String text;
    }
}
