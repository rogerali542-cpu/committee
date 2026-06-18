package com.ywh.service.quick.kb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * rule_kb 一条规则。三种 type：
 *  - regex：patterns 为正则（金额/时间/楼栋/公司）；
 *  - keyword_mapping：positive/negative（表决结果）或 mapping（资金来源/责任方）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleKbEntry {
    private String field;
    private String type;
    private List<String> patterns;
    private List<String> examples;
    private List<String> positive;
    private List<String> negative;
    private Map<String, List<String>> mapping;
}
