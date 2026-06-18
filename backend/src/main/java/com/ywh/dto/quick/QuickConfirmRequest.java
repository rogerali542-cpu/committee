package com.ywh.dto.quick;

import lombok.Data;

import java.util.List;

@Data
public class QuickConfirmRequest {
    private List<TopicResult> topics;

    @Data
    public static class TopicResult {
        private Long topicId;
        private String result; // passed | rejected | abstain | unclear
        private Boolean confirmed;
        private String summaryDraft;
        private List<Integer> segmentIndexes;
        private Integer forVotes;
        private Integer agVotes;
        private Integer abVotes;
        private Integer totalVotes;
    }

    private List<Integer> ignoredSegmentIndexes;
}
