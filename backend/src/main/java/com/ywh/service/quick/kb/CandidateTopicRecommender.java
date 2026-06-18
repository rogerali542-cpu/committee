package com.ywh.service.quick.kb;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 候选议题推荐（关键词版 v2）：拿转写文本对 KB 各议题打分，
 * 推荐 1-2 个高分、且不在预设议程里的议题填进 candidateTopics；普遍低分则不推荐。
 *
 * 一个真议题的判据不只是「内容匹配」，还要够「分量」和有「引导」，避免一句话蹭到关键词就误报：
 *   分数 = W_FAMILY·关键词覆盖 + W_VOLUME·体量占比 + W_GUIDE·引导词
 *   关键词覆盖：命中的关键词族数（同一词的多个同义词只算一族），归一到 0~1；
 *   体量占比：命中片段的字数 / 全文字数，反映该议题被讨论的篇幅；
 *   引导词：命中处附近是否出现较正式的议题引导语（"下面讨论一下""关于…的问题""我提议"…）。
 *
 * 硬门槛（全部满足才进候选）：关键词族 ≥ MIN_FAMILIES、命中片段 ≥ MIN_SEGMENTS、
 *   命中字数 ≥ MIN_CHARS、(有引导词 或 体量占比 ≥ STRONG_VOLUME_RATIO)、最终分 ≥ SCORE_THRESHOLD。
 * 阈值/权重都在常量里，按真实效果调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateTopicRecommender {

    // —— 打分权重（和为 1）——
    private static final double W_FAMILY = 0.40;
    private static final double W_VOLUME = 0.40;
    private static final double W_GUIDE = 0.20;

    // —— 归一化基准 ——
    private static final double FAMILY_DENOM = 3.0;          // 命中 3 个关键词族即覆盖满分
    private static final double VOLUME_FULL_RATIO = 0.15;    // 命中字数占全文 15% 即体量满分

    // —— 硬门槛 ——
    private static final int MIN_FAMILIES = 2;               // 至少命中 2 个关键词族
    private static final int MIN_SEGMENTS = 2;               // 至少跨 2 个片段（杜绝"一句话命中"）
    private static final int MIN_CHARS = 20;                 // 命中内容至少这么多字
    private static final double STRONG_VOLUME_RATIO = 0.20;  // 无引导词时，体量占比需达到此值才放行
    private static final double SCORE_THRESHOLD = 0.55;      // 最终分低于此不推荐
    private static final int MAX_RECOMMEND = 2;              // 最多推荐 2 个

    /** 较正式的议题引导语：出现在命中片段或其前一句，视为"被正式提起"而非顺嘴一提。 */
    private static final List<String> GUIDE_WORDS = List.of(
            "下面", "接下来", "现在进入", "现在讨论", "现在我们", "我们来讨论", "我们讨论一下", "我们说一下",
            "下一项", "下一个议题", "下一个问题", "第一项", "第二项", "第三项", "第一个议题", "第二个议题",
            "关于", "针对", "这个议题", "这个问题", "这件事", "这个事情",
            "我提议", "我建议", "我提一个", "我想提", "提一下", "商量一下", "研究一下", "讨论一下",
            "审议", "需要表决", "提交表决", "议一议"
    );

    private final KnowledgeBase kb;

    /**
     * @param presetTitles 预设议程里已有的议题标题（用于去重，避免推荐已在议程上的）
     */
    public List<QuickExtractionVO.TopicHit> recommend(AsrResult asr, List<String> presetTitles) {
        List<AsrResult.Segment> segments = asr == null || asr.getSegments() == null ? List.of() : asr.getSegments();
        if (segments.isEmpty() || kb.getTopics().isEmpty()) return List.of();

        String full = buildFullText(segments);
        long totalChars = Math.max(1, full.replace("\n", "").length());
        List<String> presets = presetTitles == null ? List.of() : presetTitles;

        List<Scored> scoredList = new ArrayList<>();
        for (TopicKbEntry topic : kb.getTopics()) {
            if (coveredByPreset(topic, presets)) continue;     // 已在议程上 → 跳过
            Scored s = score(topic, full, segments, totalChars);
            if (s != null && qualifies(s)) scoredList.add(s);
        }

        scoredList.sort(Comparator
                .comparingDouble((Scored s) -> s.score).reversed()
                .thenComparingInt(s -> -s.families));

        List<QuickExtractionVO.TopicHit> result = new ArrayList<>();
        for (int i = 0; i < scoredList.size() && i < MAX_RECOMMEND; i++) {
            result.add(toTopicHit(scoredList.get(i), segments));
        }
        if (!result.isEmpty()) {
            log.debug("[KB] 推荐候选议题 {} 个：{}", result.size(),
                    result.stream().map(QuickExtractionVO.TopicHit::getTitle).toList());
        }
        return result;
    }

    private boolean qualifies(Scored s) {
        if (s.families < MIN_FAMILIES) return false;
        if (s.matchedSegmentIdx.size() < MIN_SEGMENTS) return false;   // 不止一句话
        if (s.matchedChars < MIN_CHARS) return false;                  // 内容够分量
        if (!s.guidePresent && s.volumeRatio < STRONG_VOLUME_RATIO) return false; // 要么有引导，要么篇幅大
        return s.score >= SCORE_THRESHOLD;
    }

    // —— 打分 ——
    private Scored score(TopicKbEntry topic, String full, List<AsrResult.Segment> segments, long totalChars) {
        // 关键词族 = 每个关键词 + 议题名各算一族；命中其任一同义词即该族命中
        List<String> families = new ArrayList<>(topic.getKeywords());
        families.add(topic.getTopic());

        Set<String> matchedSurface = new LinkedHashSet<>();
        int hitFamilies = 0;
        for (String fam : families) {
            boolean hit = false;
            for (String form : kb.expand(fam)) {
                if (form != null && !form.isBlank() && full.contains(form)) {
                    hit = true;
                    matchedSurface.add(form);
                }
            }
            if (hit) hitFamilies++;
        }
        if (hitFamilies == 0) return null;

        Set<Integer> idx = new LinkedHashSet<>();
        long matchedChars = 0;
        for (int i = 0; i < segments.size(); i++) {
            String text = safe(segments.get(i).getText());
            for (String form : matchedSurface) {
                if (text.contains(form)) {
                    if (idx.add(i)) matchedChars += text.length();
                    break;
                }
            }
        }

        double familyCoverage = Math.min(1.0, hitFamilies / FAMILY_DENOM);
        double volumeRatio = (double) matchedChars / totalChars;
        double volumeScore = Math.min(1.0, volumeRatio / VOLUME_FULL_RATIO);
        boolean guidePresent = hasGuide(idx, segments);

        double score = W_FAMILY * familyCoverage + W_VOLUME * volumeScore + W_GUIDE * (guidePresent ? 1.0 : 0.0);

        Scored s = new Scored();
        s.topic = topic;
        s.families = hitFamilies;
        s.matchedChars = matchedChars;
        s.volumeRatio = volumeRatio;
        s.guidePresent = guidePresent;
        s.score = round(score);
        s.matchedSurface = new ArrayList<>(matchedSurface);
        s.matchedSegmentIdx = new ArrayList<>(idx);
        return s;
    }

    /** 命中片段或其前一句出现引导语，即视为"被正式提起"。 */
    private boolean hasGuide(Set<Integer> matchedIdx, List<AsrResult.Segment> segments) {
        for (int i : matchedIdx) {
            if (containsGuide(safe(segments.get(i).getText()))) return true;
            if (i > 0 && containsGuide(safe(segments.get(i - 1).getText()))) return true;
        }
        return false;
    }

    private boolean containsGuide(String text) {
        for (String g : GUIDE_WORDS) {
            if (text.contains(g)) return true;
        }
        return false;
    }

    // —— 与预设议题去重：互相包含，或共享 ≥2 字的关键词，则视为已覆盖 ——
    private boolean coveredByPreset(TopicKbEntry topic, List<String> presetTitles) {
        for (String raw : presetTitles) {
            String title = safe(raw);
            if (title.isBlank()) continue;
            if (title.contains(topic.getTopic()) || topic.getTopic().contains(title)) return true;
            for (String kw : topic.getKeywords()) {
                if (kw != null && kw.length() >= 2 && title.contains(kw)) return true;
            }
        }
        return false;
    }

    private QuickExtractionVO.TopicHit toTopicHit(Scored s, List<AsrResult.Segment> segments) {
        String reason = "KB候选命中：" + String.join("、", s.matchedSurface)
                + "；占比" + Math.round(s.volumeRatio * 100) + "%"
                + (s.guidePresent ? "；有议题引导" : "");
        List<QuickExtractionVO.SegmentMatch> matches = new ArrayList<>();
        for (int i : s.matchedSegmentIdx) {
            AsrResult.Segment seg = segments.get(i);
            matches.add(QuickExtractionVO.SegmentMatch.builder()
                    .segmentIndex(i)
                    .segmentId("s" + i)
                    .speaker(seg.getSpeaker())
                    .startMs(seg.getStartMs())
                    .endMs(seg.getEndMs())
                    .text(safe(seg.getText()))
                    .score(s.score)
                    .level("review")
                    .reasons(List.of(reason))
                    .build());
        }
        return QuickExtractionVO.TopicHit.builder()
                .topicId(null)
                .tempId("kb-" + s.topic.getTopic())
                .title(s.topic.getTopic())
                .type("discussion")
                .voteRequired(false)
                .matchedSegments(new ArrayList<>(s.matchedSegmentIdx))
                .segmentMatches(matches)
                .summaryDraft("会上较多提及「" + s.topic.getTopic() + "」（KB 推荐的候选议题，需人工确认是否立项）。")
                .confidence(s.score)
                .reviewLevel("review")
                .needManualReview(true)
                .voteHint(QuickExtractionVO.VoteHint.builder().result("unclear").confidence(0.0).build())
                .build();
    }

    private String buildFullText(List<AsrResult.Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (AsrResult.Segment s : segments) sb.append(safe(s.getText())).append('\n');
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static class Scored {
        private TopicKbEntry topic;
        private int families;
        private long matchedChars;
        private double volumeRatio;
        private boolean guidePresent;
        private double score;
        private List<String> matchedSurface;
        private List<Integer> matchedSegmentIdx;
    }
}
