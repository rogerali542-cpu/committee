package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.QuickPolishVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fallback used only when the LLM integration is disabled.
 * It must never fabricate demo conclusions; it only formats confirmed/real inputs.
 */
@Service
@ConditionalOnProperty(prefix = "doubao.llm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MinutesGenServiceStub implements MinutesGenService {

    @Override
    public QuickPolishVO polish(Long meetingId, QuickExtractionVO extraction, AsrResult asr) {
        return polish(meetingId, "", extraction, asr);
    }

    @Override
    public QuickPolishVO polish(Long meetingId, String meetingContext, QuickExtractionVO extraction, AsrResult asr) {
        List<QuickPolishVO.TopicSummary> topics = buildTopicSummaries(extraction);
        return QuickPolishVO.builder()
                .meetingId(meetingId)
                .topics(topics)
                .minutesMarkdown(minutesFromContext(meetingContext, topics))
                .topicReportMarkdown(topicReportFromContext(meetingContext, topics))
                .todoListMarkdown(todosFromTopics(topics))
                .fallbackUsed(true)
                .errorCode("LLM_DISABLED")
                .errorMessage("大模型未启用，已使用规则兜底")
                .source("fallback")
                .build();
    }

    @Override
    public String summarizeTopic(String title, String type, List<String> segmentTexts) {
        return TopicReportComposer.compose(title, type, segmentTexts);
    }

    private List<QuickPolishVO.TopicSummary> buildTopicSummaries(QuickExtractionVO extraction) {
        List<QuickPolishVO.TopicSummary> topics = new ArrayList<>();
        if (extraction != null && extraction.getPresetTopicHits() != null) {
            for (QuickExtractionVO.TopicHit t : extraction.getPresetTopicHits()) {
                topics.add(toSummary(t, String.valueOf(t.getTopicId())));
            }
        }
        if (extraction != null && extraction.getCandidateTopics() != null) {
            for (QuickExtractionVO.TopicHit t : extraction.getCandidateTopics()) {
                topics.add(toSummary(t, t.getTempId()));
            }
        }
        return topics;
    }

    private QuickPolishVO.TopicSummary toSummary(QuickExtractionVO.TopicHit t, String ref) {
        String title = clean(t == null ? null : t.getTitle());
        String summary = clean(t == null ? null : t.getSummaryDraft());
        if (summary.isBlank() && !title.isBlank()) {
            summary = "已围绕“" + title + "”形成会议记录，具体内容以人工确认结果为准。";
        }
        return QuickPolishVO.TopicSummary.builder()
                .ref(ref == null ? "" : ref)
                .summary(summary)
                .resolution("以人工确认结果和会议记录为准。")
                .todos(List.of())
                .build();
    }

    /** 0723 向真实纪要样张看齐：一页式叙事体（单行标题/召开情况自然段/议程逐条/列席指导/落款），不再分节。 */
    private String minutesFromContext(String meetingContext, List<QuickPolishVO.TopicSummary> topics) {
        String basic = section(meetingContext, "【会议基本信息】", "【人工确认后的议题结果】");
        String topicText = section(meetingContext, "【人工确认后的议题结果】", "【必要转写补充】");
        String community = lineValue(basic, "小区名称：");
        String org = lineValue(basic, "业委会全称（纪要抬头与落款统一使用此名称）：");
        if (org.isBlank()) org = (community.isBlank() ? "" : community) + "业主委员会";
        String time = lineValue(basic, "会议时间：");
        String location = lineValue(basic, "会议地点：");
        String host = lineValue(basic, "主持人：");
        String present = lineValue(basic, "实到委员：");
        String observers = lineValue(basic, "列席指导人员（居委/街道/物业等，非委员）：");

        StringBuilder sb = new StringBuilder();
        sb.append(community.isBlank() ? "业委会会议纪要" : community + "业委会会议纪要").append("\n\n");
        sb.append(blankToUnknown(time)).append("，").append(org.replaceAll("（[^）]*）$", ""))
                .append("在").append(blankToUnknown(location)).append("召开了业委会全体委员会议");
        if (!present.isBlank()) sb.append("，").append(present.replaceAll("；.*$", ""));
        if (!host.isBlank()) sb.append("，会议由").append(host).append("主持");
        sb.append("。\n\n");
        String renderedTopics = renderConfirmedTopics(topicText);
        if (!renderedTopics.isBlank()) {
            sb.append("会议议程及审议情况如下：\n").append(renderedTopics);
        } else if (topics != null && !topics.isEmpty()) {
            int index = 1;
            for (QuickPolishVO.TopicSummary t : topics) {
                String summary = concise(t.getSummary(), 260);
                if (!summary.isBlank()) sb.append(index++).append(". ").append(summary).append("\n");
            }
        }
        if (!observers.isBlank()) sb.append("\n").append(observers).append("等同志到会指导。\n");
        sb.append("\n").append(org).append("\n").append(cnDate(time)).append("\n");
        return sb.toString();
    }

    /** 会议日期转公文落款格式：2026-07-23 → 2026年7月23日；解析不了原样返回日期部分。 */
    private String cnDate(String time) {
        if (time == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(time);
        if (m.find()) return m.group(1) + "年" + Integer.parseInt(m.group(2)) + "月" + Integer.parseInt(m.group(3)) + "日";
        return time.split("\\s+")[0];
    }

    private String topicReportFromContext(String meetingContext, List<QuickPolishVO.TopicSummary> topics) {
        String basic = section(meetingContext, "【会议基本信息】", "【人工确认后的议题结果】");
        String topicText = section(meetingContext, "【人工确认后的议题结果】", "【必要转写补充】");
        String transcript = section(meetingContext, "【必要转写补充】", "【生成要求】");
        StringBuilder sb = new StringBuilder();
        sb.append("AI议题报告（内部保存）\n\n");
        sb.append("一、会议基础信息\n");
        sb.append(basic.isBlank() ? "未明确说明。\n" : basic).append("\n\n");
        sb.append("二、议题详细报告\n");
        if (!topicText.isBlank()) {
            sb.append(topicText).append("\n\n");
        } else if (topics != null && !topics.isEmpty()) {
            int index = 1;
            for (QuickPolishVO.TopicSummary t : topics) {
                String summary = clean(t.getSummary());
                if (!summary.isBlank()) sb.append(index++).append(". ").append(summary).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("未明确说明。\n\n");
        }
        sb.append("三、发言摘要与证据线索\n");
        sb.append(transcript.isBlank() ? "未明确说明。\n" : transcript).append("\n\n");
        sb.append("四、风险提示与生命周期\n");
        sb.append("后续应结合执行反馈补充议题状态，包括提出、讨论、修改、表决、执行、验收、归档等节点。\n");
        return sb.toString();
    }

    private String todosFromTopics(List<QuickPolishVO.TopicSummary> topics) {
        List<String> lines = new ArrayList<>();
        if (topics != null) {
            for (QuickPolishVO.TopicSummary topic : topics) {
                if (topic.getTodos() == null) continue;
                for (String todo : topic.getTodos()) {
                    String cleaned = clean(todo);
                    if (!cleaned.isBlank()) lines.add(cleaned);
                }
            }
        }
        if (lines.isEmpty()) return "无明确待办事项。";
        StringBuilder sb = new StringBuilder("待办事项\n");
        int index = 1;
        for (String line : lines) {
            sb.append(index++).append(". 事项：").append(line)
                    .append("；负责人：未明确说明；截止时间：未明确说明；状态：待完成。\n");
        }
        return sb.toString();
    }

    private String renderConfirmedTopics(String topicText) {
        if (topicText == null || topicText.isBlank()) return "";
        List<ConfirmedTopic> list = new ArrayList<>();
        ConfirmedTopic current = null;
        for (String raw : topicText.split("\\R")) {
            String line = clean(raw);
            if (line.isBlank()) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\d+\\.\\s*【([^】]+)】(.+)$").matcher(line);
            if (m.find()) {
                current = new ConfirmedTopic(m.group(1), m.group(2).trim());
                list.add(current);
                continue;
            }
            if (current == null) continue;
            if (line.startsWith("人工确认议题报告摘录：")) current.summary = line.substring("人工确认议题报告摘录：".length()).trim();
            else if (line.startsWith("表决确认结果：")) current.result = line.substring("表决确认结果：".length()).trim();
            else if (line.startsWith("表决票数：")) current.votes = line.substring("表决票数：".length()).trim();
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (ConfirmedTopic t : list) sb.append(idx++).append(". ").append(renderTopic(t)).append("\n");
        return sb.toString();
    }

    private String renderTopic(ConfirmedTopic t) {
        String summary = concise(t.summary, t.type.contains("表决") || t.type.contains("决定") ? 220 : 300);
        if (t.type.contains("通报")) {
            // 对外类型名已合并为「通知和讨论」（0717）；contains("通报") 匹配内部标签，与 Doubao 版同款联动
            return "会议听取了关于“" + t.title + "”的通报。" +
                    (summary.isBlank() ? "" : summary) +
                    "本事项为通知和讨论类议题，与会委员已知悉相关情况。";
        }
        if (t.type.contains("讨论")) {
            return "会议围绕“" + t.title + "”进行了讨论。" +
                    (summary.isBlank() ? "" : summary) +
                    "后续按会议讨论意见继续推进。";
        }
        return "会议审议了“" + t.title + "”。" +
                (summary.isBlank() ? "" : summary) +
                "经确认，表决票数为：" + blankToUnknown(t.votes) + "；表决结果为：" + blankToUnknown(t.result) + "。";
    }

    private String lineValue(String text, String prefix) {
        if (text == null) return "";
        for (String line : text.split("\\R")) {
            String cleaned = clean(line);
            if (cleaned.startsWith(prefix)) return cleaned.substring(prefix.length()).trim();
        }
        return "";
    }

    private String concise(String text, int maxLen) {
        String value = clean(text)
                .replaceAll("(通报内容|委员意见|会议结论|讨论内容|决议内容)\\s*", "")
                .replaceAll("纪要处理口径：[^。]*。?", "");
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen) + "。";
    }

    private String blankToUnknown(String text) {
        return text == null || text.isBlank() ? "未明确说明" : text;
    }

    private static class ConfirmedTopic {
        final String type;
        final String title;
        String summary = "";
        String result = "";
        String votes = "";

        ConfirmedTopic(String type, String title) {
            this.type = type == null ? "" : type;
            this.title = title == null ? "未明确说明" : title;
        }
    }

    private String section(String text, String start, String end) {
        if (text == null || text.isBlank()) return "";
        int s = text.indexOf(start);
        if (s < 0) return "";
        s += start.length();
        int e = end == null ? -1 : text.indexOf(end, s);
        String value = e >= 0 ? text.substring(s, e) : text.substring(s);
        return clean(value);
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
    }
}
