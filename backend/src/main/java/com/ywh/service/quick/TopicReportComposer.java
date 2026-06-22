package com.ywh.service.quick;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class TopicReportComposer {

    private static final Pattern TIME_SPEAKER = Pattern.compile("^\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(S\\d+|未知发言人)?\\s*");
    private static final Pattern SPEAKER_COLON = Pattern.compile("^(S\\d+|未知发言人|主持人|委员[甲乙丙丁一二三四]?|物业|物业经理|相关负责人)[:：]\\s*");
    private static final Pattern LEADING_TOPIC = Pattern.compile("^第[一二三四五六七八九十0-9]+[项个]?(议题|事项)[，,、：:]?\\s*");

    private TopicReportComposer() {
    }

    static String compose(String title, String type, List<String> segmentTexts) {
        String topicName = title == null || title.isBlank() ? "未命名议题" : title.trim();
        List<String> facts = cleanFacts(segmentTexts);
        String normalized = type == null ? "" : type.toLowerCase();
        if (normalized.contains("notice")) return notice(topicName, facts);
        if (normalized.contains("discussion")) return discussion(topicName, facts);
        return vote(topicName, facts);
    }

    private static String notice(String topicName, List<String> facts) {
        List<String> cleaning = pick(facts, "保洁", "清扫", "拖洗", "清洗", "公共区域", "楼道", "地下车库");
        List<String> greening = pick(facts, "绿化", "草坪", "绿篱", "修剪", "养护");
        List<String> otherProgress = subtract(facts, cleaning, greening, issueFacts(facts));
        List<String> issues = issueFacts(facts);

        StringBuilder sb = new StringBuilder();
        sb.append("通报内容\n");
        sb.append("会议听取了「").append(topicName).append("」事项通报。根据录音内容，本议题主要涉及以下情况：\n");
        appendSection(sb, "日常保洁方面", cleaning);
        appendSection(sb, "绿化养护方面", greening);
        appendSection(sb, "其他服务情况", otherProgress);
        if (cleaning.isEmpty() && greening.isEmpty() && otherProgress.isEmpty()) {
            sb.append("相关负责人对物业服务情况进行了说明，具体服务数据、责任人员及整改完成情况未明确说明。\n");
        }

        sb.append("\n委员意见\n");
        if (issues.isEmpty()) {
            sb.append("与会委员听取了上述通报。录音中未形成明确反对意见或补充要求，具体意见以人工核对为准。\n");
        } else {
            sb.append("与会委员对物业服务细节提出补充意见：\n");
            for (String issue : issues) sb.append("1、").append(issue).append('\n');
        }

        sb.append("\n会议结论\n");
        sb.append("本事项为通报事项，无需表决。与会委员已知悉相关物业服务情况；涉及委员补充提出的问题，建议由物业进一步核实并反馈处理情况。");
        return sb.toString();
    }

    private static String discussion(String topicName, List<String> facts) {
        List<String> suggestions = pick(facts, "建议", "希望", "需要", "应当", "可以", "方案", "优化", "公示", "梳理");
        List<String> pending = pick(facts, "再", "进一步", "待", "完善", "核实", "调查", "提交", "下次");
        StringBuilder sb = new StringBuilder();
        sb.append("议题背景\n");
        sb.append("会议围绕「").append(topicName).append("」进行讨论。");
        sb.append(facts.isEmpty() ? "录音中未明确说明具体背景。\n" : "录音中可确认的背景和问题包括：" + joinSentence(facts, 3) + "\n");
        sb.append("\n讨论情况\n");
        if (suggestions.isEmpty()) {
            sb.append("与会人员围绕该事项交换意见，但录音中未形成可明确归类的具体建议。\n");
        } else {
            int i = 1;
            for (String s : suggestions) sb.append(i++).append("、").append(s).append('\n');
        }
        sb.append("\n讨论结果\n");
        sb.append(pending.isEmpty()
                ? "本议题尚未形成明确决议，需结合人工确认结果进一步完善。"
                : "会议倾向于在补充核实、完善方案后再推进后续程序。");
        sb.append("\n\n后续安排\n");
        sb.append("责任主体：").append(findFirst(facts, "物业", "业委会", "委员会", "相关负责人")).append('\n');
        sb.append("完成时间：").append(findFirst(facts, "月底前", "月底", "一周内", "三天内", "下周", "尽快")).append('\n');
        sb.append("需补充事项：").append(pending.isEmpty() ? "未明确说明" : joinSentence(pending, 2));
        return sb.toString();
    }

    private static String vote(String topicName, List<String> facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("议题背景\n");
        sb.append("会议审议「").append(topicName).append("」事项。");
        sb.append(facts.isEmpty() ? "录音中未明确说明事项背景。\n" : "录音中可确认的事项内容包括：" + joinSentence(facts, 3) + "\n");
        sb.append("\n表决方案\n");
        sb.append(facts.isEmpty() ? "方案内容、预算金额、责任单位及实施周期未明确说明。\n" : joinLines(facts, 4));
        sb.append("\n表决情况\n");
        sb.append("应到委员：未明确说明\n实到委员：未明确说明\n同意：未明确说明\n反对：未明确说明\n弃权：未明确说明\n表决方式：未明确说明\n");
        sb.append("\n表决结果\n");
        sb.append(inferVoteResult(facts)).append('\n');
        sb.append("\n会议决议\n");
        sb.append("关于「").append(topicName).append("」的决议内容，应结合人工确认的表决票数和会议记录定稿。\n");
        sb.append("\n后续执行\n");
        sb.append("执行主体：").append(findFirst(facts, "物业", "业委会", "委员会", "施工方", "维保单位", "相关负责人")).append('\n');
        sb.append("完成时间：").append(findFirst(facts, "月底前", "月底", "一周内", "三天内", "下周", "尽快")).append('\n');
        sb.append("监督方式：未明确说明");
        return sb.toString();
    }

    private static List<String> cleanFacts(List<String> segmentTexts) {
        if (segmentTexts == null || segmentTexts.isEmpty()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : segmentTexts) {
            String s = clean(raw);
            if (s.length() < 6 || isFiller(s)) continue;
            seen.add(s);
            if (seen.size() >= 12) break;
        }
        return new ArrayList<>(seen);
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim()
                .replace('，', '，')
                .replaceAll("\\s+", " ");
        s = TIME_SPEAKER.matcher(s).replaceFirst("");
        s = SPEAKER_COLON.matcher(s).replaceFirst("");
        s = LEADING_TOPIC.matcher(s).replaceFirst("");
        s = s.replaceFirst("^(这个|那个|然后|嗯|啊|好|哎)[，,、\\s]+", "");
        return s.trim();
    }

    private static boolean isFiller(String s) {
        return s.contains("各位委员好")
                || s.contains("我插一句")
                || s.contains("待会儿说")
                || s.contains("先让经理")
                || s.contains("请物业经理介绍")
                || s.matches("^(好|嗯|可以|知道了)[。！!,.，\\s]*$");
    }

    private static List<String> pick(List<String> facts, String... keys) {
        List<String> out = new ArrayList<>();
        for (String f : facts) {
            for (String k : keys) {
                if (f.contains(k)) {
                    out.add(f);
                    break;
                }
            }
        }
        return out;
    }

    private static List<String> issueFacts(List<String> facts) {
        return pick(facts, "没人管", "堆", "问题", "投诉", "整改", "处理", "反馈", "无人管理");
    }

    @SafeVarargs
    private static List<String> subtract(List<String> src, List<String>... removes) {
        Set<String> blocked = new LinkedHashSet<>();
        for (List<String> r : removes) blocked.addAll(r);
        List<String> out = new ArrayList<>();
        for (String s : src) {
            if (!blocked.contains(s)) out.add(s);
        }
        return out;
    }

    private static void appendSection(StringBuilder sb, String label, List<String> rows) {
        if (rows.isEmpty()) return;
        sb.append(label).append("：").append(joinSentence(rows, 4)).append('\n');
    }

    private static String joinLines(List<String> rows, int limit) {
        if (rows.isEmpty()) return "未明确说明\n";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size() && i < limit; i++) {
            sb.append(i + 1).append("、").append(rows.get(i)).append('\n');
        }
        return sb.toString();
    }

    private static String joinSentence(List<String> rows, int limit) {
        if (rows.isEmpty()) return "未明确说明";
        List<String> picked = rows.subList(0, Math.min(limit, rows.size()));
        String text = String.join("；", picked);
        return text.endsWith("。") ? text : text + "。";
    }

    private static String findFirst(List<String> facts, String... keys) {
        for (String key : keys) {
            for (String fact : facts) {
                if (fact.contains(key)) return key;
            }
        }
        return "未明确说明";
    }

    private static String inferVoteResult(List<String> facts) {
        String all = String.join("。", facts);
        if (all.contains("一致通过") || all.contains("全票通过") || all.contains("无异议")) {
            return "录音中出现通过或无异议表述，具体表决结果需以人工确认票数为准。";
        }
        if (all.contains("未通过") || all.contains("暂缓") || all.contains("下次再议")) {
            return "录音中出现暂缓、再议或未通过相关表述，具体表决结果需以人工确认票数为准。";
        }
        return "录音中未明确说明表决结果，需人工核对确认。";
    }
}
