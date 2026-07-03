package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.entity.MeetingRecord;
import com.ywh.entity.RecordTopic;
import com.ywh.enums.TopicType;
import com.ywh.repository.MeetingRecordRepository;
import com.ywh.repository.RecordTopicRepository;
import com.ywh.service.quick.kb.CandidateTopicRecommender;
import com.ywh.service.quick.kb.RuleFieldExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuickExtractionServiceStub implements QuickExtractionService {

    private static final double AUTO_THRESHOLD = 0.70;
    private static final double REVIEW_THRESHOLD = 0.45;
    private static final double DECISION_AUTO_THRESHOLD = 0.75;
    private static final double DECISION_REVIEW_THRESHOLD = 0.50;

    private static final Set<String> STOP_WORDS = Set.of(
            "关于", "事项", "会议", "议题", "讨论", "审议", "研究", "工作", "有关", "进行", "情况", "通报"
    );

    // 片段去噪：去掉标点和下列语气/填充词后，有效内容少于该字数、且不含表决词/数字的片段视为无实质内容。
    private static final int MIN_MEANINGFUL_CHARS = 4;
    private static final Set<String> FILLER_TOKENS = Set.of(
            "嗯", "啊", "哦", "呃", "唉", "诶", "呀", "哈", "哈哈", "嗯嗯", "嗯呐",
            "对", "对对", "对对对", "是", "是的", "是是", "是是是", "好", "好的", "好吧", "行", "行吧",
            "ok", "OK", "然后", "就是", "这个", "那个", "你看", "你说", "反正", "所以说", "怎么说"
    );

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("维修", List.of("修缮", "维护", "施工", "整改")),
            Map.entry("预算", List.of("费用", "报价", "金额", "资金")),
            Map.entry("物业", List.of("物业公司", "服务企业", "物业服务")),
            Map.entry("停车", List.of("车位", "停车棚", "车辆")),
            Map.entry("公示", List.of("公开", "公告", "披露")),
            Map.entry("合同", List.of("协议", "续签", "签约")),
            Map.entry("安全", List.of("隐患", "消防", "整改")),
            Map.entry("表决", List.of("投票", "同意", "反对", "弃权", "通过"))
    );

    private final MeetingRecordRepository recordRepo;
    private final RecordTopicRepository topicRepo;
    private final CandidateTopicRecommender candidateRecommender;
    private final RuleFieldExtractor ruleFieldExtractor;

    @Override
    public QuickExtractionVO extract(Long meetingId, AsrResult asr) {
        List<RecordTopic> topics = recordRepo.findByMeetingId(meetingId)
                .map(MeetingRecord::getId)
                .map(topicRepo::findByRecordIdOrderBySortOrder)
                .orElse(List.of());

        Map<String, QuickExtractionVO.SpeakerInfo> speakerMap = buildSpeakerMap(asr);
        Map<String, Long> stats = buildStats(asr);
        List<AsrResult.Segment> allSegments = segments(asr);

        // 按议程顺序定位每个议题的"宣布点"，把该议题到下一议题之间的整段归入它（结构化会议归集的主力）
        int[] anchors = agendaAnchors(topics, allSegments);

        List<QuickExtractionVO.TopicHit> presetHits = new ArrayList<>();
        for (int i = 0; i < topics.size(); i++) {
            int blockStart = anchors[i];
            int blockEnd = -1;
            if (blockStart >= 0) {
                blockEnd = allSegments.size() - 1;
                for (int k = i + 1; k < anchors.length; k++) {
                    if (anchors[k] > blockStart) { blockEnd = anchors[k] - 1; break; }
                }
            }
            presetHits.add(buildPresetHit(topics.get(i), asr, i, topics.size(), blockStart, blockEnd));
        }

        List<String> presetTitles = topics.stream().map(RecordTopic::getTitle).toList();
        List<QuickExtractionVO.TopicHit> candidateTopics = candidateRecommender.recommend(asr, presetTitles);

        // rule_kb 结构化字段抽取：对每个议题命中的片段抽 金额/时间/表决结果/资金来源/责任方/公司/楼栋
        for (QuickExtractionVO.TopicHit hit : presetHits) {
            hit.setExtractedFields(ruleFieldExtractor.extract(allSegments, hit.getMatchedSegments()));
        }
        for (QuickExtractionVO.TopicHit hit : candidateTopics) {
            hit.setExtractedFields(ruleFieldExtractor.extract(allSegments, hit.getMatchedSegments()));
        }

        return QuickExtractionVO.builder()
                .meetingId(meetingId)
                .speakerMap(speakerMap)
                .presetTopicHits(presetHits)
                .candidateTopics(candidateTopics)
                .unmatchedSegments(List.of())
                .stats(stats)
                .build();
    }

    private QuickExtractionVO.TopicHit buildPresetHit(RecordTopic topic, AsrResult asr, int topicIndex, int topicCount,
                                                      int blockStart, int blockEnd) {
        List<SegmentScore> baseScores = new ArrayList<>();
        List<AsrResult.Segment> segments = segments(asr);
        for (int i = 0; i < segments.size(); i++) {
            baseScores.add(scoreSegment(topic, segments.get(i), i, segments.size(), topicIndex, topicCount, 0.0));
        }

        List<QuickExtractionVO.SegmentMatch> matches = new ArrayList<>();
        java.util.Set<Integer> have = new LinkedHashSet<>();
        double autoThreshold = autoThreshold(topic);
        double reviewThreshold = reviewThreshold(topic);
        for (int i = 0; i < baseScores.size(); i++) {
            double contextScore = contextScore(baseScores, i, reviewThreshold);
            SegmentScore scored = contextScore > 0
                    ? scoreSegment(topic, segments.get(i), i, segments.size(), topicIndex, topicCount, contextScore)
                    : baseScores.get(i);
            if (scored.score >= reviewThreshold) {
                matches.add(toMatch(scored, segments.get(i), i, scored.score >= autoThreshold ? "auto" : "review"));
                have.add(i);
            }
        }

        // 议程分块：把该议题"宣布点→下一议题前"之间、关键词没命中的实质片段也归入（跳过纯口水）
        if (blockStart >= 0 && blockEnd >= blockStart) {
            for (int i = blockStart; i <= blockEnd && i < segments.size(); i++) {
                if (have.contains(i) || isTrivialSegment(segments.get(i))) continue;
                AsrResult.Segment s = segments.get(i);
                matches.add(QuickExtractionVO.SegmentMatch.builder()
                        .segmentIndex(i).segmentId("s" + i).speaker(s.getSpeaker())
                        .startMs(s.getStartMs()).endMs(s.getEndMs()).text(safeText(s.getText()))
                        .score(round(reviewThreshold)).level("review")
                        .reasons(List.of("按议程顺序归入该议题")).build());
                have.add(i);
            }
        }

        matches.sort(Comparator.comparing(QuickExtractionVO.SegmentMatch::getSegmentIndex));
        List<Integer> matchedIndexes = matches.stream().map(QuickExtractionVO.SegmentMatch::getSegmentIndex).toList();
        double confidence = matches.stream().mapToDouble(QuickExtractionVO.SegmentMatch::getScore).max().orElse(0.0);
        String reviewLevel = confidence >= autoThreshold ? "auto" : (confidence >= reviewThreshold ? "review" : "empty");

        return QuickExtractionVO.TopicHit.builder()
                .topicId(topic.getId())
                .title(topic.getTitle())
                .type(topic.getType() != null ? topic.getType().name() : "decision")
                .voteRequired(isVoteTopic(topic))
                .matchedSegments(matchedIndexes)
                .segmentMatches(matches)
                .summaryDraft(summaryDraft(topic, matches))
                .confidence(round(confidence))
                .reviewLevel(reviewLevel)
                .needManualReview(true)
                .voteHint(isVoteTopic(topic)
                        ? voteHint(matchedIndexes, asr)
                        : QuickExtractionVO.VoteHint.builder().result("unclear").confidence(0.0).build())
                .build();
    }

    /**
     * 按议程顺序为每个议题定位"宣布点"片段下标（严格递增；定位不到为 -1）。
     * 评分 = 命中标题核心词比例×0.7 + 出现议程引导语(第X项/下面/接下来…)×0.4，从上一锚点之后向后扫描取最高分。
     */
    private int[] agendaAnchors(List<RecordTopic> topics, List<AsrResult.Segment> segs) {
        int m = topics.size(), n = segs.size();
        int[] anchor = new int[m];
        java.util.Arrays.fill(anchor, -1);
        int cursor = 0;
        for (int i = 0; i < m; i++) {
            List<String> terms = coreTerms(topics.get(i).getTitle());
            int best = -1;
            double bestScore = 0;
            for (int j = cursor; j < n; j++) {
                String text = safeText(segs.get(j).getText());
                if (text.isEmpty()) continue;
                double s = 0;
                if (!terms.isEmpty()) {
                    long tm = terms.stream().filter(text::contains).count();
                    s += 0.7 * tm / terms.size();
                }
                if (containsAny(text, "第一项", "第二项", "第三项", "第四项", "第五项", "第六项",
                        "下一项", "下一个议题", "下一个问题", "下面", "接下来", "现在进入", "这一项", "最后一项")) {
                    s += 0.4;
                }
                if (s > bestScore) { bestScore = s; best = j; }
            }
            if (best >= 0 && bestScore >= 0.5) { anchor[i] = best; cursor = best + 1; }
        }
        return anchor;
    }

    private List<QuickExtractionVO.UnmatchedSegment> buildUnmatchedSegments(List<RecordTopic> topics,
                                                                            List<QuickExtractionVO.TopicHit> hits,
                                                                            AsrResult asr) {
        List<AsrResult.Segment> segments = segments(asr);
        Set<Integer> matched = new LinkedHashSet<>();
        for (QuickExtractionVO.TopicHit hit : hits) {
            if (hit.getMatchedSegments() != null) matched.addAll(hit.getMatchedSegments());
        }

        List<QuickExtractionVO.UnmatchedSegment> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            if (matched.contains(i)) continue;
            if (isTrivialSegment(segments.get(i))) continue;   // 语气词/口水话等明显无需归类的片段，过滤掉
            BestTopic best = bestTopic(topics, segments.get(i), i, segments.size());
            AsrResult.Segment s = segments.get(i);
            result.add(QuickExtractionVO.UnmatchedSegment.builder()
                    .segmentIndex(i)
                    .segmentId("s" + i)
                    .speaker(s.getSpeaker())
                    .startMs(s.getStartMs())
                    .endMs(s.getEndMs())
                    .text(safeText(s.getText()))
                    .bestTopicId(best.topicId)
                    .bestTopicTitle(best.title)
                    .bestScore(round(best.score))
                    .build());
        }
        return result;
    }

    private BestTopic bestTopic(List<RecordTopic> topics, AsrResult.Segment segment, int segmentIndex, int segmentCount) {
        BestTopic best = new BestTopic();
        for (int i = 0; i < topics.size(); i++) {
            SegmentScore score = scoreSegment(topics.get(i), segment, segmentIndex, segmentCount, i, topics.size(), 0.0);
            if (score.score > best.score) {
                best.score = score.score;
                best.topicId = topics.get(i).getId();
                best.title = topics.get(i).getTitle();
            }
        }
        return best;
    }

    private SegmentScore scoreSegment(RecordTopic topic,
                                      AsrResult.Segment segment,
                                      int segmentIndex,
                                      int segmentCount,
                                      int topicIndex,
                                      int topicCount,
                                      double contextScore) {
        String text = safeText(segment.getText());
        List<String> titleTerms = coreTerms(topic.getTitle());
        List<String> keywords = expandedKeywords(titleTerms);
        List<String> reasons = new ArrayList<>();

        long matchedTitleTerms = titleTerms.stream().filter(text::contains).count();
        double titleScore = titleTerms.isEmpty() ? 0.0 : 0.25 * matchedTitleTerms / titleTerms.size();
        if (matchedTitleTerms > 0) reasons.add("命中议题核心词：" + joinMatched(text, titleTerms));

        double keywordHits = keywordHitWeight(text, keywords);
        double keywordScore = 0.25 * Math.min(1.0, keywordHits / 3.0);
        if (keywordHits > 0) reasons.add("命中关键词/同义词");

        boolean hasGuide = containsAny(text, "下面", "接下来", "现在进入", "关于", "这个议题", "第一个", "第二个", "第三个", "下一个");
        double guideScore = hasGuide ? (matchedTitleTerms > 0 ? 0.15 : 0.06) : 0.0;
        if (guideScore > 0) reasons.add("出现议题引导语");

        double orderScore = orderScore(segmentIndex, segmentCount, topicIndex, topicCount);
        if (orderScore >= 0.07) reasons.add("位置接近议题顺序");

        double typeScore = typeScore(topic, text);
        if (typeScore > 0) reasons.add("命中事项类型表达");

        if (contextScore > 0) reasons.add("前后片段指向同一议题");

        QuickExtractionVO.ScoreDetail detail = QuickExtractionVO.ScoreDetail.builder()
                .titleScore(round(titleScore))
                .keywordScore(round(keywordScore))
                .guideScore(round(guideScore))
                .orderScore(round(orderScore))
                .typeScore(round(typeScore))
                .contextScore(round(contextScore))
                .build();

        double score = titleScore + keywordScore + guideScore + orderScore + typeScore + contextScore;
        return new SegmentScore(round(Math.min(1.0, score)), detail, reasons);
    }

    private double contextScore(List<SegmentScore> baseScores, int index, double reviewThreshold) {
        boolean prev = index > 0 && baseScores.get(index - 1).score >= reviewThreshold;
        boolean next = index + 1 < baseScores.size() && baseScores.get(index + 1).score >= reviewThreshold;
        if (prev && next) return 0.10;
        if (prev || next) return 0.07;
        return 0.0;
    }

    private QuickExtractionVO.SegmentMatch toMatch(SegmentScore scored, AsrResult.Segment s, int index, String level) {
        return QuickExtractionVO.SegmentMatch.builder()
                .segmentIndex(index)
                .segmentId("s" + index)
                .speaker(s.getSpeaker())
                .startMs(s.getStartMs())
                .endMs(s.getEndMs())
                .text(safeText(s.getText()))
                .score(scored.score)
                .level(level)
                .reasons(scored.reasons)
                .scoreDetail(scored.detail)
                .build();
    }

    private String summaryDraft(RecordTopic topic, List<QuickExtractionVO.SegmentMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return "暂无明确匹配内容，可人工补充纪要。";
        }
        String type = topic.getType() == null ? "decision" : topic.getType().name();
        if ("notice".equals(type)) return "会议对该事项进行了通报说明，具体内容以已确认片段为准。";
        if ("discussion".equals(type)) return "会议围绕该事项进行了讨论，形成意见和后续动作需人工确认。";
        return "会议对该事项进行了表决，表决结果需人工确认。";
    }

    private QuickExtractionVO.VoteHint voteHint(List<Integer> matched, AsrResult asr) {
        if (matched == null || matched.isEmpty() || asr == null || asr.getSegments() == null) {
            return QuickExtractionVO.VoteHint.builder().result("unclear").confidence(0.2).source("none").build();
        }
        // 汇总命中片段及其前后邻句的文本（表决播报常在议题尾部）
        Set<Integer> idxs = new LinkedHashSet<>();
        int n = asr.getSegments().size();
        for (Integer m : matched) {
            for (int i = Math.max(0, m - 1); i <= Math.min(n - 1, m + 2); i++) idxs.add(i);
        }
        StringBuilder sb = new StringBuilder();
        for (int i : idxs) sb.append(safeText(asr.getSegments().get(i).getText())).append(' ');
        String text = sb.toString();

        // 1) 显式播报票数：「X票同意 / X票反对 / X票弃权」
        Integer fav = findCount(text, "(?:同意|赞成)");
        Integer ag = findCount(text, "(?:反对|不同意)");
        Integer ab = findCount(text, "弃权");
        if (fav != null || ag != null || ab != null) {
            int f = fav == null ? 0 : fav, a = ag == null ? 0 : ag, b = ab == null ? 0 : ab;
            return QuickExtractionVO.VoteHint.builder()
                    .result(f >= a ? "passed" : "rejected").confidence(0.9)
                    .forVotes(f).agVotes(a).abVotes(b).unanimous(false).source("explicit").build();
        }

        // 2) 全票/一致通过：票数按实到人数在前端补齐（这里只标记）
        if (containsAny(text, "全票通过", "一致通过", "全体通过", "无异议", "没有异议")) {
            return QuickExtractionVO.VoteHint.builder()
                    .result("passed").confidence(0.85).unanimous(true).source("unanimous").build();
        }
        if (containsAny(text, "否决", "未通过", "不予通过", "未形成决议")) {
            return QuickExtractionVO.VoteHint.builder()
                    .result("rejected").confidence(0.8).unanimous(false).source("unanimous").build();
        }

        // 3) 逐个表态计数（兜底，粗略）：数「同意/赞成」与「反对/不同意」「弃权」出现次数
        int agree = countOcc(text, "同意", "赞成") - countOcc(text, "不同意");
        int reject = countOcc(text, "反对", "不同意", "不通过", "暂缓");
        int abstain = countOcc(text, "弃权");
        if (agree <= 0 && reject <= 0 && abstain <= 0) {
            return QuickExtractionVO.VoteHint.builder().result("unclear").confidence(0.35).source("none").build();
        }
        agree = Math.max(0, agree);
        return QuickExtractionVO.VoteHint.builder()
                .result(agree >= reject ? "passed" : "rejected")
                .confidence(round(Math.min(0.7, 0.4 + Math.max(agree, reject) * 0.1)))
                .forVotes(agree).agVotes(reject).abVotes(abstain).unanimous(false).source("counted").build();
    }

    /**
     * 在文本里找某表态的票数，支持两种语序（中文数字亦可）；找不到返回 null。
     *   ①「关键词 + 数字 + 单位」：同意七票 / 同意 7 人（口播常见语序，优先）
     *   ②「数字 + (票) + 关键词」：七票同意 / 7 人赞成
     */
    private Integer findCount(String text, String kw) {
        String num = "([0-9零〇○两一二三四五六七八九十]{1,4})";
        // ① 关键词在前：需带单位(票/人/名/个/张)以免把"同意该方案"之类误判
        java.util.regex.Matcher m1 = java.util.regex.Pattern
                .compile(kw + "\\s*(?:的|了|有|共)?\\s*" + num + "\\s*(?:票|人|名|个|张)")
                .matcher(text);
        if (m1.find()) return chineseToInt(m1.group(1));
        // ② 数字在前
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile(num + "\\s*票?\\s*(?:人?\\s*)?" + kw)
                .matcher(text);
        if (m2.find()) return chineseToInt(m2.group(1));
        return null;
    }

    /** 统计若干词在文本中出现的总次数。 */
    private int countOcc(String text, String... words) {
        int c = 0;
        for (String w : words) {
            int from = 0, i;
            while ((i = text.indexOf(w, from)) >= 0) { c++; from = i + w.length(); }
        }
        return c;
    }

    /** 中文/阿拉伯数字转 int（0~99，业委会人数量级足够）；无法解析返回 null。 */
    private Integer chineseToInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.matches("\\d{1,3}")) return Integer.parseInt(s);
        s = s.replace("两", "二").replace("〇", "零").replace("○", "零");
        String d = "零一二三四五六七八九";
        if (!s.contains("十")) {
            int v = 0;
            for (char c : s.toCharArray()) {
                int p = d.indexOf(c);
                if (p < 0) return null;
                v = v * 10 + p;
            }
            return v;
        }
        int idx = s.indexOf('十');
        int tens = idx == 0 ? 1 : d.indexOf(s.charAt(0));
        int ones = idx == s.length() - 1 ? 0 : d.indexOf(s.charAt(idx + 1));
        if (tens < 0 || ones < 0) return null;
        return tens * 10 + ones;
    }

    private Map<String, QuickExtractionVO.SpeakerInfo> buildSpeakerMap(AsrResult asr) {
        Map<String, QuickExtractionVO.SpeakerInfo> speakerMap = new LinkedHashMap<>();
        for (AsrResult.Segment s : segments(asr)) {
            String speaker = s.getSpeaker() == null || s.getSpeaker().isBlank() ? "S?" : s.getSpeaker();
            speakerMap.putIfAbsent(speaker, QuickExtractionVO.SpeakerInfo.builder().memberId(null).name(speaker).build());
        }
        return speakerMap;
    }

    private Map<String, Long> buildStats(AsrResult asr) {
        Map<String, Long> stats = new HashMap<>();
        for (AsrResult.Segment s : segments(asr)) {
            stats.merge(s.getSpeaker(), Math.max(0, s.getEndMs() - s.getStartMs()), Long::sum);
        }
        return stats;
    }

    private List<AsrResult.Segment> segments(AsrResult asr) {
        if (asr == null || asr.getSegments() == null) return List.of();
        return asr.getSegments();
    }

    private boolean isVoteTopic(RecordTopic topic) {
        return topic != null && topic.getType() != TopicType.notice && topic.getType() != TopicType.discussion;
    }

    private double autoThreshold(RecordTopic topic) {
        return isVoteTopic(topic) ? DECISION_AUTO_THRESHOLD : AUTO_THRESHOLD;
    }

    private double reviewThreshold(RecordTopic topic) {
        return isVoteTopic(topic) ? DECISION_REVIEW_THRESHOLD : REVIEW_THRESHOLD;
    }

    private double orderScore(int segmentIndex, int segmentCount, int topicIndex, int topicCount) {
        if (segmentCount <= 0 || topicCount <= 0) return 0.0;
        double segmentPos = (segmentIndex + 0.5) / segmentCount;
        double topicPos = (topicIndex + 0.5) / topicCount;
        double distance = Math.abs(segmentPos - topicPos);
        return round(0.10 * Math.max(0.0, 1.0 - distance * topicCount));
    }

    private double typeScore(RecordTopic topic, String text) {
        String type = topic.getType() == null ? "decision" : topic.getType().name();
        if ("notice".equals(type) && containsAny(text, "通报", "说明", "汇报", "告知", "传达", "学习", "情况如下")) return 0.12;
        if ("discussion".equals(type) && containsAny(text, "建议", "认为", "方案", "意见", "是否", "怎么", "后续", "安排")) return 0.12;
        if (containsAny(text, "表决", "同意", "反对", "弃权", "通过", "未通过", "举手", "投票")) return 0.15;
        return 0.0;
    }

    private List<String> coreTerms(String title) {
        String normalized = safeText(title).replaceAll("[\\p{Punct}\\s，。；：、（）《》“”‘’【】]", " ");
        List<String> terms = new ArrayList<>();
        for (String part : normalized.split("\\s+")) {
            String p = part.trim();
            if (p.length() >= 2 && !STOP_WORDS.contains(p)) terms.add(p);
        }
        String compact = normalized.replaceAll("\\s+", "");
        for (String stop : STOP_WORDS) compact = compact.replace(stop, "");
        if (compact.length() >= 2) {
            terms.add(compact);
            for (String key : SYNONYMS.keySet()) {
                if (compact.contains(key)) terms.add(key);
            }
            for (List<String> values : SYNONYMS.values()) {
                for (String value : values) {
                    if (compact.contains(value)) terms.add(value);
                }
            }
        }
        return terms.stream().distinct().toList();
    }

    private List<String> expandedKeywords(List<String> titleTerms) {
        LinkedHashSet<String> words = new LinkedHashSet<>(titleTerms);
        for (String term : titleTerms) {
            for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
                if (term.contains(entry.getKey()) || entry.getKey().contains(term)) {
                    words.add(entry.getKey());
                    words.addAll(entry.getValue());
                }
            }
        }
        return new ArrayList<>(words);
    }

    private double keywordHitWeight(String text, List<String> keywords) {
        double hits = 0.0;
        for (String word : keywords) {
            if (text.contains(word)) {
                hits += SYNONYMS.containsKey(word) ? 1.0 : 0.8;
            }
        }
        return hits;
    }

    private String joinMatched(String text, List<String> terms) {
        return String.join("、", terms.stream().filter(text::contains).toList());
    }

    /**
     * 判断片段是否「明显无需归类」（语气词、附和、口水话等）。
     * 保守起见：含表决词或数字（金额/比例/日期）的短句一律保留，避免误删关键内容。
     */
    private boolean isTrivialSegment(AsrResult.Segment segment) {
        String compact = safeText(segment.getText())
                .replaceAll("[\\p{Punct}\\s，。；：、？！…—·（）《》“”‘’【】]", "");
        if (compact.isEmpty()) return true;
        if (containsAny(compact, "同意", "赞成", "通过", "反对", "不同意", "弃权", "未通过", "表决", "投票")) return false;
        if (compact.matches(".*\\d.*")) return false;   // 含数字：金额/比例/日期等，保留
        String stripped = compact;
        for (String filler : FILLER_TOKENS) stripped = stripped.replace(filler, "");
        return stripped.length() < MIN_MEANINGFUL_CHARS;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) return true;
        }
        return false;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class SegmentScore {
        private final double score;
        private final QuickExtractionVO.ScoreDetail detail;
        private final List<String> reasons;

        private SegmentScore(double score, QuickExtractionVO.ScoreDetail detail, List<String> reasons) {
            this.score = score;
            this.detail = detail;
            this.reasons = reasons;
        }
    }

    private static class BestTopic {
        private Long topicId;
        private String title;
        private double score;
    }
}
