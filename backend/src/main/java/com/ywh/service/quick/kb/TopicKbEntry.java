package com.ywh.service.quick.kb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** topic_kb 一条议题：二级议题名 + 父类 + 常见结构字段 + 关键词。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicKbEntry {
    private String topic;
    @JsonProperty("parent_topic")
    private String parentTopic;
    private List<String> fields;
    private List<String> keywords;
}
