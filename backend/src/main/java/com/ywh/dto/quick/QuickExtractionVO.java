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
    }
}
