package com.ywh.dto.quick;

import lombok.Data;

import java.util.List;

@Data
public class TopicSummaryRequest {
    private String title;
    private String type;
    private List<Integer> segmentIndexes;
    private List<String> segmentTexts;
}
