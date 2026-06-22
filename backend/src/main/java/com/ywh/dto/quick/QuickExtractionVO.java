package com.ywh.dto.quick;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Rule-layer extraction for quick committee meetings.
 * The result is only a suggestion. The miniapp must require manual confirmation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickExtractionVO {
    private Long meetingId;
    private Map<String, SpeakerInfo> speakerMap;
    private List<TopicHit> presetTopicHits;
    private List<TopicHit> candidateTopics;
    private List<UnmatchedSegment> unmatchedSegments;
    private Map<String, Long> stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpeakerInfo {
        private Long memberId;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicHit {
        private Long topicId;
        private String tempId;
        private String title;
        private String type;
        private Boolean voteRequired;
        private List<Integer> matchedSegments;
        private List<SegmentMatch> segmentMatches;
        private String summaryDraft;
        private double confidence;
        private String reviewLevel; // auto | review | empty
        private Boolean needManualReview;
        private VoteHint voteHint;
        private List<ExtractedField> extractedFields; // rule_kb 抽取的结构化字段（金额/时间/表决结果/资金来源/责任方/公司/楼栋）
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedField {
        private String field;            // 金额 | 时间 | 表决结果 | 资金来源 | 责任方 | 公司/单位 | 楼栋/位置
        private String type;             // regex | keyword_mapping
        private List<FieldValue> values; // 命中的具体值（去重，带来源片段佐证）
        private String normalized;       // keyword_mapping 的归一化结论（表决:passed/rejected/unclear；资金来源/责任方:标签）；regex 为 null
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldValue {
        private String value;         // 命中的原文值（regex 抽到的串 / mapping 命中的词）
        private Integer segmentIndex; // 来源片段下标
        private String evidence;      // 来源片段全文，作为佐证
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentMatch {
        private Integer segmentIndex;
        private String segmentId;
        private String speaker;
        private Long startMs;
        private Long endMs;
        private String text;
        private double score;
        private String level; // auto | review
        private List<String> reasons;
        private ScoreDetail scoreDetail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreDetail {
        private double titleScore;
        private double keywordScore;
        private double guideScore;
        private double orderScore;
        private double typeScore;
        private double contextScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnmatchedSegment {
        private Integer segmentIndex;
        private String segmentId;
        private String speaker;
        private Long startMs;
        private Long endMs;
        private String text;
        private Long bestTopicId;
        private String bestTopicTitle;
        private double bestScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoteHint {
        private String result;     // passed | rejected | unclear
        private double confidence; // 0~1
        private Integer forVotes;  // 识别出的同意票数；未识别为 null
        private Integer agVotes;   // 反对
        private Integer abVotes;   // 弃权
        private Boolean unanimous; // 是否"全票/一致通过"（票数按实到人数在前端补齐）
        private String source;     // explicit(播报票数) | unanimous(全票/一致) | counted(逐个表态计数) | none
    }
}
