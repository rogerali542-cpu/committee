package com.ywh.service.quick.kb;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO.ExtractedField;
import com.ywh.dto.quick.QuickExtractionVO.FieldValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 rule_kb 的结构化字段抽取：对某个议题命中的转写片段，按规则抽出
 * 金额 / 时间 / 公司单位 / 楼栋位置（regex），以及表决结果 / 资金来源 / 责任方（keyword_mapping）。
 *
 * 只在给定片段范围内抽取（议题的命中片段），结果作为「建议值」附带来源佐证，前端需人工确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleFieldExtractor {

    private final KnowledgeBase kb;

    /**
     * @param segments       全量转写片段
     * @param segmentIndexes 该议题命中的片段下标（抽取只在这些片段内进行）
     */
    public List<ExtractedField> extract(List<AsrResult.Segment> segments, List<Integer> segmentIndexes) {
        if (segments == null || segments.isEmpty() || segmentIndexes == null || segmentIndexes.isEmpty()) {
            return List.of();
        }
        if (kb.getRules().isEmpty()) return List.of();

        List<ExtractedField> out = new ArrayList<>();
        for (RuleKbEntry rule : kb.getRules()) {
            ExtractedField field = "regex".equalsIgnoreCase(rule.getType())
                    ? extractRegex(rule, segments, segmentIndexes)
                    : extractKeywordMapping(rule, segments, segmentIndexes);
            if (field != null && field.getValues() != null && !field.getValues().isEmpty()) {
                out.add(field);
            }
        }
        return out;
    }

    // —— regex 类：金额 / 时间 / 公司单位 / 楼栋位置 ——
    private ExtractedField extractRegex(RuleKbEntry rule, List<AsrResult.Segment> segments, List<Integer> idxs) {
        List<Pattern> patterns = kb.getCompiledPatterns().get(rule.getField());
        if (patterns == null || patterns.isEmpty()) return null;

        Map<String, FieldValue> values = new LinkedHashMap<>(); // 按值去重，保留首次佐证
        for (int i : idxs) {
            if (i < 0 || i >= segments.size()) continue;
            String text = safe(segments.get(i).getText());
            if (text.isEmpty()) continue;
            for (Pattern p : patterns) {
                Matcher m = p.matcher(text);
                while (m.find()) {
                    String v = m.group().trim();
                    if (v.isEmpty()) continue;
                    values.putIfAbsent(v, FieldValue.builder()
                            .value(v).segmentIndex(i).evidence(text).build());
                }
            }
        }
        if (values.isEmpty()) return null;
        return ExtractedField.builder()
                .field(rule.getField()).type("regex")
                .values(new ArrayList<>(values.values())).normalized(null)
                .build();
    }

    // —— keyword_mapping 类：表决结果(positive/negative) 或 资金来源/责任方(mapping) ——
    private ExtractedField extractKeywordMapping(RuleKbEntry rule, List<AsrResult.Segment> segments, List<Integer> idxs) {
        boolean isVote = (rule.getPositive() != null && !rule.getPositive().isEmpty())
                || (rule.getNegative() != null && !rule.getNegative().isEmpty());
        return isVote ? extractVote(rule, segments, idxs) : extractMapping(rule, segments, idxs);
    }

    private ExtractedField extractVote(RuleKbEntry rule, List<AsrResult.Segment> segments, List<Integer> idxs) {
        Map<String, FieldValue> values = new LinkedHashMap<>();
        int pos = collect(rule.getPositive(), segments, idxs, values);
        int neg = collect(rule.getNegative(), segments, idxs, values);
        if (values.isEmpty()) return null;
        String normalized = pos > neg ? "passed" : (neg > pos ? "rejected" : "unclear");
        return ExtractedField.builder()
                .field(rule.getField()).type("keyword_mapping")
                .values(new ArrayList<>(values.values())).normalized(normalized)
                .build();
    }

    private ExtractedField extractMapping(RuleKbEntry rule, List<AsrResult.Segment> segments, List<Integer> idxs) {
        if (rule.getMapping() == null || rule.getMapping().isEmpty()) return null;
        Map<String, FieldValue> values = new LinkedHashMap<>();
        List<String> hitLabels = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : rule.getMapping().entrySet()) {
            int before = values.size();
            collect(entry.getValue(), segments, idxs, values);
            if (values.size() > before) hitLabels.add(entry.getKey()); // 该标签下有同义词命中
        }
        if (values.isEmpty()) return null;
        return ExtractedField.builder()
                .field(rule.getField()).type("keyword_mapping")
                .values(new ArrayList<>(values.values()))
                .normalized(String.join("、", hitLabels))
                .build();
    }

    /** 在片段内查找 words 任一词，命中的词记入 values（按词去重）；返回命中次数（按片段累计）。 */
    private int collect(List<String> words, List<AsrResult.Segment> segments, List<Integer> idxs,
                        Map<String, FieldValue> values) {
        if (words == null || words.isEmpty()) return 0;
        int hits = 0;
        for (int i : idxs) {
            if (i < 0 || i >= segments.size()) continue;
            String text = safe(segments.get(i).getText());
            if (text.isEmpty()) continue;
            for (String w : words) {
                if (w != null && !w.isBlank() && text.contains(w)) {
                    hits++;
                    values.putIfAbsent(w, FieldValue.builder()
                            .value(w).segmentIndex(i).evidence(text).build());
                }
            }
        }
        return hits;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
