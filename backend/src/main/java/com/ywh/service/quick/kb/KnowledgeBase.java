package com.ywh.service.quick.kb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 业委会会议议题知识库：启动时从 classpath:kb/*.json 载入 topic / synonym / rule。
 * 任一文件缺失或损坏只记日志、不阻断启动（对应能力降级，而非整个应用挂掉）。
 */
@Slf4j
@Component
@Getter
public class KnowledgeBase {

    private static final String TOPIC_FILE = "kb/topic_kb_v2.json";
    private static final String SYNONYM_FILE = "kb/synonym_kb_v2.json";
    private static final String RULE_FILE = "kb/rule_kb_v2.json";

    private final ObjectMapper mapper;

    private List<TopicKbEntry> topics = List.of();
    private Map<String, List<String>> synonyms = Map.of();
    private List<RuleKbEntry> rules = List.of();
    /** rule_kb 中 regex 规则预编译：field -> 已编译 patterns。 */
    private Map<String, List<Pattern>> compiledPatterns = Map.of();

    public KnowledgeBase(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void load() {
        this.topics = loadTopics();
        this.synonyms = loadSynonyms();
        this.rules = loadRules();
        this.compiledPatterns = compilePatterns(this.rules);
        log.info("[KB] 载入完成：topics={} synonyms={} rules={} regexFields={}",
                topics.size(), synonyms.size(), rules.size(), compiledPatterns.size());
    }

    private List<TopicKbEntry> loadTopics() {
        try {
            List<TopicKbEntry> list = mapper.readValue(
                    new ClassPathResource(TOPIC_FILE).getInputStream(),
                    new TypeReference<List<TopicKbEntry>>() {});
            int dropped = 0;
            List<TopicKbEntry> valid = new ArrayList<>();
            for (TopicKbEntry t : list) {
                if (t.getTopic() == null || t.getTopic().isBlank()) { dropped++; continue; }
                if (t.getKeywords() == null) t.setKeywords(List.of());
                valid.add(t);
            }
            if (dropped > 0) log.warn("[KB] topic_kb 跳过 {} 条无 topic 名的条目", dropped);
            return valid;
        } catch (Exception e) {
            log.error("[KB] 载入 {} 失败，议题库为空", TOPIC_FILE, e);
            return List.of();
        }
    }

    private Map<String, List<String>> loadSynonyms() {
        try {
            return mapper.readValue(
                    new ClassPathResource(SYNONYM_FILE).getInputStream(),
                    new TypeReference<LinkedHashMap<String, List<String>>>() {});
        } catch (Exception e) {
            log.error("[KB] 载入 {} 失败，同义词库为空", SYNONYM_FILE, e);
            return Map.of();
        }
    }

    private List<RuleKbEntry> loadRules() {
        try {
            return mapper.readValue(
                    new ClassPathResource(RULE_FILE).getInputStream(),
                    new TypeReference<List<RuleKbEntry>>() {});
        } catch (Exception e) {
            log.error("[KB] 载入 {} 失败，规则库为空", RULE_FILE, e);
            return List.of();
        }
    }

    private Map<String, List<Pattern>> compilePatterns(List<RuleKbEntry> rules) {
        Map<String, List<Pattern>> map = new LinkedHashMap<>();
        int bad = 0;
        for (RuleKbEntry r : rules) {
            if (!"regex".equalsIgnoreCase(r.getType()) || r.getPatterns() == null) continue;
            List<Pattern> compiled = new ArrayList<>();
            for (String p : r.getPatterns()) {
                try {
                    compiled.add(Pattern.compile(p));
                } catch (Exception e) {
                    bad++;
                    log.warn("[KB] rule_kb 字段[{}] 正则编译失败已跳过：{}", r.getField(), p);
                }
            }
            if (!compiled.isEmpty()) map.put(r.getField(), compiled);
        }
        if (bad > 0) log.warn("[KB] rule_kb 共 {} 条正则编译失败", bad);
        return Collections.unmodifiableMap(map);
    }

    /** 取某关键词的同义词扩展（含自身）。key 命中同义词表则并入其列表，否则仅返回自身。 */
    public List<String> expand(String term) {
        if (term == null || term.isBlank()) return List.of();
        List<String> syn = synonyms.get(term);
        if (syn == null || syn.isEmpty()) return List.of(term);
        List<String> out = new ArrayList<>(syn.size() + 1);
        out.add(term);
        out.addAll(syn);
        return out;
    }
}
