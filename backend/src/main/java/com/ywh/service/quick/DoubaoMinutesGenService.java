package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ywh.config.DoubaoProperties;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.QuickPolishVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 豆包大模型（方舟 Ark / Seed）真实接入：把规则层命中 + 转写口语 → 书面结构化纪要。
 * 仅当 doubao.llm.enabled=true 时启用（否则用 {@link MinutesGenServiceStub} 的演示纪要）。
 *
 * 表决结论以人工确认为准——这里只做「口语→书面」的整理，决议字段标注"待人工确认"，不替人定表决。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "doubao.llm", name = "enabled", havingValue = "true")
public class DoubaoMinutesGenService implements MinutesGenService {

    private final DoubaoProperties props;
    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String SYSTEM_PROMPT = """
            你是专业的业主委员会会议秘书。依据【委员意见】、【人工确认议题结果】、【会议转写】和【议题线索】（议题审议以【委员意见】为主、录音转写为辅），同时生成三份产物：
            1. 正式会议纪要 minutesMarkdown：用于存档、公示和展示，必须简洁正式；
            2. AI议题报告 topicReportMarkdown：用于系统内部保存，要点完整但精炼，不堆砌、不重复、不冗长；
            3. 待办事项 todoListMarkdown：用于执行跟踪，结构化列出事项、负责人、截止时间、来源议题、状态。

            议题类型判断：
            1. 通报类 notice：议题或发言包含“汇报、通报、介绍、学习、传达、进展情况、工作情况”等，通常无需表决。
            2. 讨论类 discussion：议题或发言包含“讨论、研究、商议、征求意见、协商、建议”等，通常形成意见但暂不表决。
            3. 表决类 vote：系统中可能标为 decision；议题或发言包含“表决、审议、投票、决议、通过、不通过、选聘、解聘”等，需要形成正式决议。

            各类议题关注点：
            - notice：议题名称、汇报人、事项背景、当前情况/进展、已采取措施、下一步安排、委员意见、会议结论、后续动作。
            - discussion：议题名称、主持人、事项背景、主要观点（支持意见、反对意见、修改建议、需进一步调查问题）、讨论共识、讨论结果、责任人、完成时间。
            - vote/decision：议题名称、主持人、事项背景、方案内容、预算金额、实施周期、责任单位、应到/实到人数、同意/反对/弃权票数、表决方式、表决结果、会议决议、执行主体、完成时间、监督方式。

            处理步骤：
            1. 对每个议题先按【信息提取清单】尽量完整提取事实；清单中的缺失项写“未明确说明”。
            2. 内部 AI 议题报告保留详细背景、详细通报/讨论/方案内容、委员意见、问题列表、风险提示、整改事项、责任人、完成时限和议题生命周期状态。
            3. 正式会议纪要（minutesMarkdown）必须仿照真实业委会一页式纪要：单行标题、自然段正文、落款和日期；不设会议信息块，不设分节小标题，不写成工作报告，详见第 6 条。
            4. 待办事项采用“会后行动”标准：只要会议内容明确表达了会后需要有人继续做某件事，就必须提取为待办。
               不要求同时具备负责人和截止时间；缺失时分别写“未明确说明”。包括但不限于：安排维修/检查/采购/联系/报批/整改，
               跟进进度、补充材料、调查核实、修改方案、再次沟通或提交后续会议。纯背景介绍、已完成事项、仅供知悉且没有后续动作的内容不算待办。
               判断不确定但存在明确行动表达时优先提取，后续由主任人工确认或删除；确实没有会后动作才写“无明确待办事项”。

            硬性要求：
            1. 只依据转写内容，不得编造未出现的事实、人名、金额或决议。
            2. 无法确认的信息写“未明确说明”，不得虚构委员发言或表决人数。
            3. 「决议」字段只整理会上已形成的口径、表决播报或讨论倾向，并在结尾注明"（待人工确认）"；切勿替与会者下最终表决结论。
            4. 语言简洁、正式，统一使用第三人称，去掉口头语和重复；金额/数字/时间/单位、表决票数与应到/实到人数必须与给定信息完全一致，不得改写、四舍五入或估算。
            5. 待办（todos）是会上明确要求会后继续执行或跟进的事项；负责人、截止时间未明确也要提取，不得因此漏掉。没有会后动作才给空数组。
            6. minutesMarkdown 面向全体业主公示，必须严格仿照真实样本的“一页式决定摘要”（纯文本，不使用 Markdown 标题符号）：
               （1）第一行且仅一行标题：「{小区名称}业委会会议纪要」。不要另写会议名称、期数、编号或第二行“会议纪要”。
               （2）标题后直接写自然段，不得设置“会议时间、会议地点、参会人员、主持人”等字段式信息块，也不得设置“一、会议基本情况”“二、议题审议情况”“会议结论与后续安排”等小标题。
               （3）第一自然段用完整句交代会议日期、召开地点或线上方式、参会情况和主持人。现场与线上参会并存时必须如实写清；请假、缺席原因只在已明确时写。
               （4）第二自然段写会议议程。多项议题压缩在同一自然段，用“一是……；二是……；三是……”串联，不逐人罗列意见，不展开背景材料，不写内部分析。
               （5）第三自然段集中写会议形成的结果：通知和讨论类事项写知悉情况或形成的意见，表决事项写同意、反对、弃权或“原则同意”等真实结果，并紧接着写已经明确的公示、修改、实施等下一步动作。没有明确动作则不补写。
               （6）如有居委会、街道等人员到会指导，可在正文最后用一句话客观记录；没有则省略。
               （7）落款两行：第一行「{业委会全称}」，第二行会议日期（形如“2026年7月8日”）。不得生成委员签名、主持人签名或盖章文字；印章由正式文件线下加盖。
               （8）全文以一页为目标，通常3至5个自然段；宁可简短，不得为了结构完整重复人数、票数、议题或后续安排。
            7. minutesMarkdown 宁简勿繁、以够用为度，不追求篇幅；作为公开文件不得逐字记录委员发言，不得写入内部风险评估、证据线索或未经确认的猜测。
            8. topicReportMarkdown 必须是详细版内部报告：可保留业务统计、发言摘要、证据线索和风险提示。
            9. 通报类事项在 minutesMarkdown 中不得写“赞成、反对、通过、未通过、表决”等表决口径；只写通报内容、委员知悉/意见和后续安排。
            10. 表决/决议类事项在 minutesMarkdown 中只保留足以说明决定的方案名称、真实表决结果和紧接着的办理动作；逐人投票、完整票数明细、表决过程和详细执行清单属于会议记录，不写入纪要。若人工确认内容明确出现“原则同意”等保留意见，必须原样保留其人数和表述，不得擅自归并为同意或弃权。
            11. 严格只输出一个 JSON 对象，不要任何解释或 Markdown 代码块包裹。

            12. 意见提炼（opinions）：以该议题下的【委员意见】为主逐条整理（委员在线提交/认领的本人意见）；【会议转写】中出现而【委员意见】遗漏的明确表态可补入。把每位发言人对该议题明确表达的意见/立场/建议逐条整理出来：
                - text 用第一人称、书面化改写（像该委员自己写下的意见），忠实原意、不揣测立场，每条 60 字以内；
                - speaker 填转写中该发言的发言人（有真实姓名用姓名，只有编号就原样填 S1、说话人2 等）；
                - 只提炼明确的意见表达（同意/反对/建议/疑问/补充），主持流程话术、寒暄和纯事实汇报不算意见；
                - 同一人对同一议题的多次同类表态合并成一条；没有意见就给空数组。

            输出 JSON 结构：
            {
              "topics": [
                {"ref": "议题标识(原样回填)", "summary": "讨论摘要", "resolution": "决议结论（待人工确认）", "todos": ["待办1","待办2"],
                 "opinions": [{"speaker": "发言人姓名或编号", "text": "第一人称书面化意见"}]}
              ],
              "minutesMarkdown": "严格按第6条真实样本格式撰写的一页式会议纪要（单行标题/自然段召开情况/一是二是议程/集中结果与动作/落款日期）",
              "topicReportMarkdown": "AI议题报告，详细版，用于内部保存",
              "todoListMarkdown": "待办事项清单，包含事项、负责人、截止时间、来源议题、状态"
            }
            """;

    private static final String MINUTES_SPEC = """
            议题类型与关注点：
            - 通报类 notice：关注汇报人、事项背景、当前情况/进展、已采取措施、下一步安排、委员意见、会议结论、后续动作。若确无讨论，写“与会委员已知悉相关情况，无异议。”结论为“本事项为通知和讨论类议题，与会委员已知悉相关情况。”（对外类型名统一为「通知和讨论」，不要写“通报事项”）
            - 讨论类 discussion：关注主持人、事项背景、支持意见、反对意见、修改建议、需进一步调查的问题、讨论共识、讨论结果、责任人、完成时间。结果可为“原则同意、暂缓决定、补充调查、修改完善后再次讨论、提交业主大会表决”等，但只能依据会议内容。
            - 表决类 vote/decision：关注主持人、事项背景、方案内容、预算金额、实施周期、责任单位、应到/实到人数、同意/反对/弃权票数、表决方式、表决结果、会议决议、执行主体、完成时间、监督方式。不得虚构票数，无法确认写“未明确说明”。
            """;

    @Override
    public QuickPolishVO polish(Long meetingId, QuickExtractionVO extraction, AsrResult asr) {
        return polish(meetingId, "", extraction, asr);
    }

    @Override
    public QuickPolishVO polish(Long meetingId, String meetingContext, QuickExtractionVO extraction, AsrResult asr) {
        try {
            String userContent = buildUserContent(meetingContext, extraction, asr);
            String content = callLlm(userContent);
            QuickPolishVO vo = parse(meetingId, content);
            if (vo != null) {
                vo.setFallbackUsed(false);
                vo.setSource("llm");
                return vo;
            }
            log.warn("[MINUTES] 豆包返回无法解析为纪要 JSON，降级。meetingId={}", meetingId);
            return fallback(meetingId, meetingContext, extraction, "LLM_PARSE_FAILED", "大模型返回格式异常，已使用规则兜底");
        } catch (Exception e) {
            LlmFailure failure = classifyLlmFailure(e);
            log.error("[MINUTES] 豆包纪要生成失败，降级。meetingId={} code={} message={}", meetingId, failure.code, failure.message, e);
            return fallback(meetingId, meetingContext, extraction, failure.code, failure.message);
        }
    }

    private static final String TOPIC_SUMMARY_SYSTEM = """
            你是业主委员会会议纪要助理。把某个议题的口语化讨论整理成一份正式、客观、书面的议题报告。
            要求：
            1. 只依据给定内容，不得编造未出现的事实、人名、金额、时间或决议。
            2. 无法确认的信息写“未明确说明”；不得虚构委员发言或表决人数。
            3. 去掉口头语、重复和无关闲聊；金额/数字/时间/单位保持准确。
            4. 尽量把给定内容里的背景、方案、金额、时间、责任人、意见、结论、后续安排都提取出来。
            5. 不要输出录音时间戳、片段序号或 S1/S2 等说话人编号。
            6. 输出纯文本，不要 JSON、不要 Markdown、不要额外解释。
            """;

    /** 通告/通报事项固定模板：通告内容 / 委员意见 / 会议结论 三段。 */
    private static final String NOTICE_SUMMARY_SYSTEM = """
            你是业主委员会会议纪要助理。把某个【通报类事项】的口语化讨论整理成规范、客观、书面的议题报告，严格按固定模板输出。
            要求：
            1. 只依据给定内容，不得编造未出现的事实、人名、金额、时间或决议；无法确认的信息写“未明确说明”；去掉口头语、重复和无关闲聊；金额/数字/时间/单位保持准确。
            2. 严格按以下模板输出纯文本，保留三个小标题，不要 Markdown、不要 JSON、不要额外解释或开场白：

            通报内容
            由XX（主任/物业/专项负责人，按内容判断；判断不出就写“相关负责人”）介绍本事项，主要内容如下：
            1、事项背景：……
            2、当前进展：……
            3、已采取措施：……
            4、下一步安排：……

            委员意见
            委员们听取了上述情况汇报，并就以下方面进行了交流：
            （逐条列出委员提出的意见或补充；如确无讨论，则整段改为一句：与会委员知悉相关情况，无异议。）

            会议结论
            本事项为通知和讨论类议题，与会委员已知悉相关情况。
            """;

    private static final String DISCUSSION_SUMMARY_SYSTEM = """
            你是业主委员会会议纪要助理。把某个【讨论类事项】整理成规范、客观、书面的议题报告。
            重点关注：事项背景、需要讨论的问题、委员主要观点、支持意见、反对意见、修改建议、需进一步调查的问题、讨论共识、讨论结果、后续安排、责任人和完成时间。
            要求：
            1. 只依据给定内容，不得编造未出现的事实、人名、金额、时间或结论；无法确认的信息写“未明确说明”。
            2. 可形成“原则同意、暂缓决定、补充调查、修改完善后再次讨论、提交业主大会表决”等讨论结果，但必须来自会议内容。
            3. 严格按以下模板输出纯文本，保留小标题，不要 JSON、不要 Markdown、不要额外解释：

            议题背景
            （说明事项产生原因、当前情况及需要讨论的问题）

            讨论情况
            1、支持意见：……
            2、反对意见：……
            3、修改建议：……
            4、需进一步调查的问题：……

            讨论结果
            根据会议讨论情况，形成如下意见：……

            后续安排
            责任人：……
            完成时间：……
            """;

    private static final String VOTE_SUMMARY_SYSTEM = """
            你是业主委员会会议纪要助理。把某个【表决类事项】整理成规范、客观、书面的议题报告。
            重点关注：事项背景、方案内容、预算金额、实施周期、责任单位、应到/实到人数、同意/反对/弃权票数、表决方式、表决结果、会议决议、执行主体、完成时间和监督方式。
            要求：
            1. 只依据给定内容，不得编造未出现的事实、人名、金额、时间、票数或决议；无法确认的信息写“未明确说明”。
            2. 表决结果和会议决议以会议转写或人工确认信息为准；不能从倾向性讨论推断最终通过/未通过。
            3. 严格按以下模板输出纯文本，保留小标题，不要 JSON、不要 Markdown、不要额外解释：

            议题背景
            （说明事项背景、方案内容及表决依据）

            表决方案
            1、方案内容：……
            2、预算金额：……
            3、实施周期：……
            4、责任单位：……

            表决情况
            应到委员：……
            实到委员：……
            同意：……
            反对：……
            弃权：……
            表决方式：……

            表决结果
            根据表决结果：……

            会议决议
            1、……
            2、……

            后续执行
            执行主体：……
            完成时间：……
            监督方式：……
            """;

    @Override
    public String summarizeTopic(String title, String type, List<String> segmentTexts) {
        if (segmentTexts == null || segmentTexts.isEmpty()) return localTopicReport(title, type, List.of());
        List<String> cleanedTexts = cleanTopicSegmentTexts(segmentTexts);
        try {
            String sys = topicSummarySystem(type);
            StringBuilder u = new StringBuilder();
            u.append("议题：").append(title == null ? "" : title).append('\n');
            u.append("类型：").append(type == null ? "未明确说明" : type).append('\n');
            u.append("信息提取清单：").append(topicFocus(type)).append('\n');
            u.append("请先尽量完整提取上述清单中的事实，再生成正式议题报告；缺失项写“未明确说明”；不要保留时间戳、片段序号或说话人编号。\n");
            u.append("会议片段正文：\n");
            for (String s : cleanedTexts) {
                if (s != null && !s.isBlank()) u.append("· ").append(s.trim()).append('\n');
            }
            String out = callLlmText(sys, u.toString());
            if (out != null && !out.isBlank()) return stripTranscriptMarks(out);
            return localTopicReport(title, type, cleanedTexts);
        } catch (Exception e) {
            log.error("[MINUTES] 单议题摘要失败 title=" + title, e);
            return localTopicReport(title, type, cleanedTexts);
        }
    }

    private String localTopicReport(String title, String type, List<String> segmentTexts) {
        return TopicReportComposer.compose(title, type, segmentTexts);
    }

    private List<String> cleanTopicSegmentTexts(List<String> segmentTexts) {
        if (segmentTexts == null || segmentTexts.isEmpty()) return List.of();
        List<String> cleaned = new ArrayList<>();
        for (String raw : segmentTexts) {
            if (raw == null) continue;
            String s = raw.trim()
                    .replaceAll("^\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*", "")
                    .replaceAll("^\\s*(S\\d+|未知发言人)\\s+", "")
                    .replaceAll("^\\s*(S\\d+|未知发言人)[:：]\\s*", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!s.isBlank()) cleaned.add(s);
        }
        return cleaned;
    }

    private List<String> compactFacts(List<String> segmentTexts) {
        if (segmentTexts == null || segmentTexts.isEmpty()) return List.of();
        List<String> facts = new ArrayList<>();
        for (String text : segmentTexts) {
            if (text == null) continue;
            String s = text.trim();
            if (s.isBlank()) continue;
            s = s.replaceAll("^(S\\d+|未知发言人|[^：]{1,12})：", "").trim();
            if (s.length() < 4) continue;
            facts.add((facts.size() + 1) + "、" + s);
            if (facts.size() >= 8) break;
        }
        return facts;
    }

    private String inferResult(List<String> segmentTexts, String kind) {
        String all = joinTexts(segmentTexts);
        if (all.contains("一致通过") || all.contains("全票通过") || all.contains("无异议") || all.contains("没有异议")) {
            return "根据会议讨论，现场出现通过或无异议表述，具体结论需以人工确认结果为准。";
        }
        if (all.contains("暂缓") || all.contains("下次再议") || all.contains("未通过") || all.contains("未形成决议")) {
            return "根据会议讨论，现场出现暂缓、再议或未通过相关表述，具体结论需以人工确认结果为准。";
        }
        return "会议已围绕该" + kind + "事项进行说明和讨论，最终意见需人工核对确认。";
    }

    private String extractVoteText(List<String> segmentTexts) {
        String all = joinTexts(segmentTexts);
        List<String> rows = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[^。；;\\n]*(同意|赞成|反对|弃权|通过|未通过|无异议|全票|一致)[^。；;\\n]*")
                .matcher(all);
        while (m.find() && rows.size() < 6) {
            String s = m.group().trim();
            if (!s.isBlank()) rows.add((rows.size() + 1) + "、" + s);
        }
        if (!rows.isEmpty()) return String.join("\n", rows);
        return "应到委员：未明确说明\n实到委员：未明确说明\n同意：未明确说明\n反对：未明确说明\n弃权：未明确说明\n表决方式：未明确说明";
    }

    private String firstMatch(List<String> segmentTexts, String... words) {
        String all = joinTexts(segmentTexts);
        for (String word : words) {
            if (all.contains(word)) return word;
        }
        return "未明确说明";
    }

    private String joinTexts(List<String> segmentTexts) {
        if (segmentTexts == null || segmentTexts.isEmpty()) return "";
        return String.join("。", segmentTexts);
    }

    private String topicSummarySystem(String type) {
        String normalized = type == null ? "" : type.toLowerCase();
        if (normalized.contains("notice")) return NOTICE_SUMMARY_SYSTEM;
        if (normalized.contains("discussion")) return DISCUSSION_SUMMARY_SYSTEM;
        if (normalized.contains("decision") || normalized.contains("vote")) return VOTE_SUMMARY_SYSTEM;
        return TOPIC_SUMMARY_SYSTEM + "\n" + MINUTES_SPEC + "\n请客观概述讨论要点与倾向，表决结论以人工确认为准、不要替会议下结论。";
    }

    // —— 组装给模型的用户内容 ——
    private String buildUserContent(String meetingContext, QuickExtractionVO extraction, AsrResult asr) {
        StringBuilder sb = new StringBuilder();

        if (meetingContext != null && !meetingContext.isBlank()) {
            sb.append(meetingContext.trim()).append("\n\n");
            sb.append("请严格依据以上【会议基本信息】【人工确认后的议题结果】及其中每个议题的【委员意见】生成正式会议纪要；");
            sb.append("【委员意见】是「议题审议情况」的主要内容来源，【必要转写补充】仅用于补漏和核对事实，不要逐句复述转写。\n");
            sb.append("纪要严格采用真实业委会一页式样本：单行“小区名称+业委会会议纪要”标题；自然段交代日期、地点或线上平台、参会情况和主持人；用“一是、二是、三是”在同一自然段概括议程；随后集中写真实结果与已明确的紧接办理动作；最后仅写业委会全称和日期。不得设信息块、章节小标题或逐项报告。\n");
            sb.append("正式纪要只保留决定摘要，不逐字复述、不给每人单列一段；详细委员意见和投票明细留在内部议题报告与会议记录。\n");
            sb.append("语言精炼平实、直接说事，严禁套话空话——禁用“为进一步/以…为契机/切实推进/扎实做好/高度重视/统一思想/凝聚共识/在…关心指导下”等；能一句说清就不写两句，信息不足直接省略、不要写“未明确说明”。\n");
            sb.append("通报类事项不得写赞成、反对、通过、未通过或表决，只写通报内容、委员意见和后续安排；");
            sb.append("表决/决议类事项只写足以说明决定的事项名称和总体结果；明确存在“原则同意”等保留意见时原样保留。不得写逐人投票、完整明细、内部风险或证据线索。\n");
            sb.append("表决票数、表决结果、应到/实到人数一律以【人工确认后的议题结果】为准；与【必要转写补充】冲突时以人工确认为准，人工确认未给出的数字不得从转写或委员意见推断、编造。\n");
            sb.append("另外：topics[].ref 按【人工确认后的议题结果】中的议题顺序填对应序号（1、2、3…），");
            sb.append("每个议题的 opinions 以该议题的【委员意见】为主逐条整理（第一人称书面化、点到发言人），【必要转写补充】里出现而【委员意见】遗漏的明确表态可补入；都没有则给空数组。\n");
            return sb.toString();
        }

        sb.append("【议题线索】（请按 ref 原样回填，preset 用议题ID，临时议题用 tempId）\n");
        if (extraction != null && extraction.getPresetTopicHits() != null) {
            for (QuickExtractionVO.TopicHit t : extraction.getPresetTopicHits()) {
                appendTopic(sb, String.valueOf(t.getTopicId()), t, false);
            }
        }
        if (extraction != null && extraction.getCandidateTopics() != null) {
            for (QuickExtractionVO.TopicHit t : extraction.getCandidateTopics()) {
                appendTopic(sb, t.getTempId(), t, true);
            }
        }
        if (sb.indexOf("- ref=") < 0) {
            sb.append("（无结构化议题命中，请直接依据转写归纳主要议题）\n");
        }

        sb.append("\n【会议转写】\n");
        Map<String, QuickExtractionVO.SpeakerInfo> speakers =
                extraction != null ? extraction.getSpeakerMap() : null;
        if (asr != null && asr.getSegments() != null) {
            for (AsrResult.Segment seg : asr.getSegments()) {
                String name = seg.getSpeaker();
                if (speakers != null && speakers.get(seg.getSpeaker()) != null
                        && speakers.get(seg.getSpeaker()).getName() != null) {
                    name = speakers.get(seg.getSpeaker()).getName();
                }
                sb.append(name).append("：").append(seg.getText() == null ? "" : seg.getText()).append('\n');
            }
        } else {
            sb.append("（无转写内容）\n");
        }
        return sb.toString();
    }

    private void appendTopic(StringBuilder sb, String ref, QuickExtractionVO.TopicHit t, boolean candidate) {
        sb.append("- ref=").append(ref)
                .append(candidate ? " [临时议题]" : "")
                .append(" 标题：").append(t.getTitle() == null ? "" : t.getTitle());
        if (t.getType() != null) sb.append(" 类型：").append(t.getType());
        sb.append(" 纪要关注点：").append(topicFocus(t.getType()));
        if (Boolean.TRUE.equals(t.getVoteRequired())) sb.append(" 需表决");
        if (t.getVoteHint() != null && t.getVoteHint().getResult() != null) {
            sb.append(" 表决倾向(仅参考)：").append(t.getVoteHint().getResult());
            if (t.getVoteHint().getForVotes() != null) sb.append(" 同意票：").append(t.getVoteHint().getForVotes());
            if (t.getVoteHint().getAgVotes() != null) sb.append(" 反对票：").append(t.getVoteHint().getAgVotes());
            if (t.getVoteHint().getAbVotes() != null) sb.append(" 弃权票：").append(t.getVoteHint().getAbVotes());
            if (t.getVoteHint().getSource() != null) sb.append(" 来源：").append(t.getVoteHint().getSource());
        }
        sb.append('\n');
        appendExtractionChecklist(sb, t.getType());
        appendExtractedFields(sb, t);
        if (t.getSegmentMatches() != null) {
            for (QuickExtractionVO.SegmentMatch m : t.getSegmentMatches()) {
                if (m.getText() != null && !m.getText().isBlank()) {
                    sb.append("    · ").append(m.getText()).append('\n');
                }
            }
        }
    }

    private String topicFocus(String type) {
        String normalized = type == null ? "" : type.toLowerCase();
        if (normalized.contains("notice")) {
            return "汇报人、事项背景、当前情况/进展、已采取措施、下一步安排、委员意见、会议结论、后续动作";
        }
        if (normalized.contains("discussion")) {
            return "主持人、事项背景、主要观点、支持/反对意见、修改建议、需进一步调查问题、讨论结果、责任人、完成时间";
        }
        return "主持人、事项背景、方案内容、预算金额、实施周期、责任单位、应到/实到人数、同意/反对/弃权票数、表决方式、表决结果、会议决议、执行主体、完成时间、监督方式";
    }

    private void appendExtractionChecklist(StringBuilder sb, String type) {
        String normalized = type == null ? "" : type.toLowerCase();
        sb.append("    信息提取清单（尽量从下方片段提取；缺失写“未明确说明”）：\n");
        if (normalized.contains("notice")) {
            sb.append("    - 议题名称、汇报人、事项背景、当前情况/进展、已采取措施、下一步安排、委员意见、会议结论、后续动作\n");
            return;
        }
        if (normalized.contains("discussion")) {
            sb.append("    - 议题名称、主持人、事项背景、需要讨论的问题、支持意见、反对意见、修改建议、需进一步调查的问题、讨论共识、讨论结果、后续安排、责任人、完成时间\n");
            return;
        }
        sb.append("    - 议题名称、主持人、事项背景、方案内容、预算金额、实施周期、责任单位、应到委员、实到委员、同意票、反对票、弃权票、表决方式、表决结果、会议决议、执行主体、完成时间、监督方式\n");
    }

    private void appendExtractedFields(StringBuilder sb, QuickExtractionVO.TopicHit t) {
        if (t.getExtractedFields() == null || t.getExtractedFields().isEmpty()) return;
        sb.append("    已抽取字段：\n");
        for (QuickExtractionVO.ExtractedField field : t.getExtractedFields()) {
            if (field == null || field.getField() == null) continue;
            List<String> values = new ArrayList<>();
            if (field.getNormalized() != null && !field.getNormalized().isBlank()) {
                values.add("归一化=" + field.getNormalized());
            }
            if (field.getValues() != null) {
                for (QuickExtractionVO.FieldValue value : field.getValues()) {
                    if (value != null && value.getValue() != null && !value.getValue().isBlank()) {
                        values.add(value.getValue());
                    }
                }
            }
            if (!values.isEmpty()) {
                sb.append("    - ").append(field.getField()).append("：")
                        .append(String.join("、", values.stream().distinct().toList())).append('\n');
            }
        }
    }

    // —— 调用方舟 chat/completions ——
    private String callLlm(String userContent) throws Exception {
        DoubaoProperties.Llm llm = props.getLlm();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", 0.3);
        // 输出封顶：防止模型把三份文档写得过长拖慢生成；配合"精炼"prompt，正常纪要足够用。
        body.put("max_tokens", 4096);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", userContent);
        body.putObject("response_format").put("type", "json_object");

        String url = trimTrailingSlash(llm.getBaseUrl()) + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llm.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("Ark chat status=" + resp.statusCode() + " body=" + resp.body());
        }
        JsonNode root = mapper.readTree(resp.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    // —— 调用方舟 chat/completions（纯文本返回，不强制 JSON）——
    private String callLlmText(String system, String userContent) throws Exception {
        DoubaoProperties.Llm llm = props.getLlm();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", 0.3);
        body.put("max_tokens", 1800);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", userContent);

        String url = trimTrailingSlash(llm.getBaseUrl()) + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(150))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llm.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("Ark chat status=" + resp.statusCode() + " body=" + resp.body());
        }
        JsonNode root = mapper.readTree(resp.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    // —— 解析模型输出 ——
    private QuickPolishVO parse(Long meetingId, String content) throws Exception {
        if (content == null || content.isBlank()) return null;
        // 容错：模型可能用```json 包裹或夹带前后文字，截取首个 { 到末个 }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        JsonNode root = mapper.readTree(content.substring(start, end + 1));

        List<QuickPolishVO.TopicSummary> topics = new ArrayList<>();
        JsonNode arr = root.path("topics");
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                List<String> todos = new ArrayList<>();
                if (n.path("todos").isArray()) {
                    n.path("todos").forEach(td -> {
                        String s = td.asText("");
                        if (!s.isBlank()) todos.add(s);
                    });
                }
                List<QuickPolishVO.OpinionDraft> opinions = new ArrayList<>();
                if (n.path("opinions").isArray()) {
                    for (JsonNode op : n.path("opinions")) {
                        String text = stripTranscriptMarks(op.path("text").asText(""));
                        if (text.isBlank()) continue;
                        opinions.add(QuickPolishVO.OpinionDraft.builder()
                                .speaker(op.path("speaker").asText("").trim())
                                .text(text)
                                .build());
                    }
                }
                topics.add(QuickPolishVO.TopicSummary.builder()
                        .ref(n.path("ref").asText(""))
                        .summary(stripTranscriptMarks(n.path("summary").asText("")))
                        .resolution(n.path("resolution").asText(""))
                        .todos(todos)
                        .opinions(opinions)
                        .build());
            }
        }
        return QuickPolishVO.builder()
                .meetingId(meetingId)
                .topics(topics)
                // 正式纪要含「会议时间 14:30」等真实时钟，仅清说话人编号与括号内时间戳；
                // 内部议题报告无时钟字段，按整段清洗，连同录音时间戳一并去掉。
                .minutesMarkdown(stripSpeakerArtifacts(root.path("minutesMarkdown").asText("")))
                .topicReportMarkdown(stripTranscriptMarks(root.path("topicReportMarkdown").asText("")))
                .todoListMarkdown(stripSpeakerArtifacts(root.path("todoListMarkdown").asText("")))
                .build();
    }

    // —— 调用失败/解析失败时的兜底：用规则层标题先撑住 UI ——
    private QuickPolishVO fallback(Long meetingId, String meetingContext, QuickExtractionVO extraction,
                                  String errorCode, String errorMessage) {
        List<QuickPolishVO.TopicSummary> topics = new ArrayList<>();
        if (extraction != null && extraction.getPresetTopicHits() != null) {
            for (QuickExtractionVO.TopicHit t : extraction.getPresetTopicHits()) {
                topics.add(QuickPolishVO.TopicSummary.builder()
                        .ref(String.valueOf(t.getTopicId()))
                        .summary(t.getSummaryDraft() == null ? "" : t.getSummaryDraft())
                        .resolution("（待人工确认）")
                        .todos(new ArrayList<>())
                        .build());
            }
        }
        return QuickPolishVO.builder()
                .meetingId(meetingId)
                .topics(topics)
                .minutesMarkdown(fallbackMinutes(meetingContext, topics))
                .topicReportMarkdown(fallbackTopicReport(meetingContext, topics))
                .todoListMarkdown(fallbackTodos(topics))
                .fallbackUsed(true)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .source("fallback")
                .build();
    }

    private LlmFailure classifyLlmFailure(Exception e) {
        String message = e == null ? "" : String.valueOf(e.getMessage());
        String lower = message.toLowerCase();
        if (e instanceof java.net.http.HttpTimeoutException || lower.contains("timeout") || lower.contains("timed out")) {
            return new LlmFailure("LLM_TIMEOUT", "大模型请求超时，已使用规则兜底");
        }
        if (message.contains("status=401") || message.contains("status=403")) {
            return new LlmFailure("LLM_AUTH_FAILED", "大模型鉴权失败，请检查 API Key、模型权限或账号额度");
        }
        if (message.contains("status=429")) {
            return new LlmFailure("LLM_RATE_LIMITED", "大模型调用触发限流，已使用规则兜底");
        }
        if (message.matches("(?s).*status=5\\d\\d.*")) {
            return new LlmFailure("LLM_SERVER_ERROR", "大模型服务端异常，已使用规则兜底");
        }
        if (lower.contains("json") || lower.contains("parse")) {
            return new LlmFailure("LLM_PARSE_FAILED", "大模型返回格式异常，已使用规则兜底");
        }
        return new LlmFailure("LLM_CALL_FAILED", "大模型调用失败，已使用规则兜底");
    }

    private static class LlmFailure {
        final String code;
        final String message;

        LlmFailure(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private String fallbackMinutes(String meetingContext, List<QuickPolishVO.TopicSummary> topics) {
        String basic = section(meetingContext, "【会议基本信息】", "【人工确认后的议题结果】");
        String topicText = section(meetingContext, "【人工确认后的议题结果】", "【必要转写补充】");
        StringBuilder sb = new StringBuilder();
        String community = field(basic, "小区名称");
        sb.append((community.isBlank() ? "业委会" : community + "业委会")).append("会议纪要\n\n");
        String date = field(basic, "会议时间");
        String location = field(basic, "会议地点");
        String host = field(basic, "主持人");
        sb.append(date.isBlank() ? "本次" : date).append("，业主委员会");
        if (!location.isBlank()) sb.append("在").append(location);
        sb.append("召开会议");
        if (!host.isBlank()) sb.append("，会议由").append(host).append("主持");
        sb.append("。\n\n会议的议程：");
        String renderedTopics = renderConfirmedTopics(topicText);
        if (!renderedTopics.isBlank()) {
            sb.append(renderedTopics.replaceAll("(?m)^\\s*\\d+[.、]\\s*", "").replace("\n", "；"));
        } else if (topics != null && !topics.isEmpty()) {
            int index = 1;
            for (QuickPolishVO.TopicSummary topic : topics) {
                String summary = concise(topic.getSummary(), 260);
                if (!summary.isBlank()) {
                    sb.append(index++ == 1 ? "一是" : "；二是").append(summary);
                }
            }
        } else {
            sb.append("会议事项已按人工确认结果记录");
        }
        sb.append("。\n\n会议按照人工确认结果形成相应意见和决定。\n\n");
        sb.append(field(basic, "业委会全称（纪要抬头与落款统一使用此名称）")).append("\n");
        if (!date.isBlank()) sb.append(date.replaceFirst("\\s+.*$", ""));
        return sb.toString();
    }

    private String field(String block, String label) {
        if (block == null || block.isBlank()) return "";
        String prefix = label + "：";
        for (String line : block.split("\\R")) {
            String value = line.trim();
            if (value.startsWith(prefix)) return value.substring(prefix.length()).trim();
        }
        return "";
    }

    private String fallbackTopicReport(String meetingContext, List<QuickPolishVO.TopicSummary> topics) {
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
            for (QuickPolishVO.TopicSummary topic : topics) {
                String summary = clean(topic.getSummary());
                if (!summary.isBlank()) {
                    sb.append(index++).append(". ").append(summary).append('\n');
                }
            }
            sb.append('\n');
        } else {
            sb.append("未明确说明。\n\n");
        }
        sb.append("三、发言摘要与证据线索\n");
        sb.append(transcript.isBlank() ? "未明确说明。\n" : transcript).append("\n\n");
        sb.append("四、风险提示与生命周期\n");
        sb.append("后续应结合执行反馈补充议题状态，包括提出、讨论、修改、表决、执行、验收、归档等节点。\n");
        return sb.toString();
    }

    private String fallbackTodos(List<QuickPolishVO.TopicSummary> topics) {
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

    private void appendBasicMinutes(StringBuilder sb, String basic) {
        String title = lineValue(basic, "会议名称：");
        String time = lineValue(basic, "会议时间：");
        String location = lineValue(basic, "会议地点：");
        String host = lineValue(basic, "主持人：");
        String present = lineValue(basic, "实到委员：");
        String total = lineValue(basic, "应到委员：");
        sb.append("会议名称：").append(blankToUnknown(title)).append("\n");
        sb.append("会议时间：").append(blankToUnknown(time)).append("\n");
        sb.append("会议地点：").append(blankToUnknown(location)).append("\n");
        sb.append("主持人：").append(blankToUnknown(host)).append("\n");
        sb.append("参会情况：应到委员").append(blankToUnknown(total)).append("，实到委员").append(blankToUnknown(present)).append("。\n");
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
        for (ConfirmedTopic t : list) {
            sb.append(idx++).append(". ").append(renderTopic(t)).append("\n");
        }
        return sb.toString();
    }

    private String renderTopic(ConfirmedTopic t) {
        String summary = concise(t.summary, "表决类".equals(t.type) || "决定事项".equals(t.type) ? 220 : 300);
        if (t.type != null && t.type.contains("通报")) {
            // 对外类型名已合并为「通知和讨论」（0717），此处仅结论措辞随之调整；contains("通报") 匹配的
            // 是 CommitteeService.topicTypeLabel 的内部标签，两边联动，别单改一处
            return "会议听取了关于“" + t.title + "”的通报。" +
                    (summary.isBlank() ? "" : summary) +
                    "本事项为通知和讨论类议题，与会委员已知悉相关情况。";
        }
        if (t.type != null && t.type.contains("讨论")) {
            return "会议围绕“" + t.title + "”进行了讨论。" +
                    (summary.isBlank() ? "" : summary) +
                    "后续按会议讨论意见继续推进。";
        }
        String result = blankToUnknown(t.result);
        String votes = blankToUnknown(t.votes);
        return "会议审议了“" + t.title + "”。" +
                (summary.isBlank() ? "" : summary) +
                "经确认，表决票数为：" + votes + "；表决结果为：" + result + "。";
    }

    /** 兜底文本清理：去掉 Markdown 强调/标题/列表符号与多余空白。 */
    private String clean(String text) {
        if (text == null) return "";
        return text.replace("*", "")
                .replace("`", "")
                .replaceAll("^\\s*#{1,6}\\s*", "")
                .replaceAll("^[\\s\\-—·•]+", "")
                .replaceAll("[ \\t\\u3000]{2,}", " ")
                .trim();
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
        return value.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // —— 输出侧清洗：大模型有时会把转写里的录音时间戳（00:15）和说话人编号（S1）回显进纪要 ——
    /** 括号包裹的录音时间戳：（00:15）[01:02:03]【0:30】等。 */
    private static final java.util.regex.Pattern TS_BRACKET =
            java.util.regex.Pattern.compile("[（(\\[【]\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[)）\\]】]");
    /** 独立的录音时间戳 mm:ss / hh:mm:ss（前后非数字、非冒号，避免误伤被包裹的真实时钟）。 */
    private static final java.util.regex.Pattern TS_TOKEN =
            java.util.regex.Pattern.compile("(?<![\\d:])\\d{1,2}:\\d{2}(?::\\d{2})?(?![\\d:])");
    /** 说话人编号 S1/S2…（后接标点、空白或行尾）。 */
    private static final java.util.regex.Pattern SPEAKER_TAG =
            java.util.regex.Pattern.compile("S\\d{1,3}(?=[：:、，,。.\\s)）\\]】]|$)");

    /** 仅清说话人编号与括号内时间戳，保留正文里的真实时钟（如「会议时间 14:30」）。用于正式纪要。 */
    private static String stripSpeakerArtifacts(String text) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        String s = TS_BRACKET.matcher(text).replaceAll("");
        s = SPEAKER_TAG.matcher(s).replaceAll("");
        return tidy(s);
    }

    /** 整段清洗：连同独立录音时间戳一并去掉。用于无时钟字段的议题摘要/议题报告。 */
    private static String stripTranscriptMarks(String text) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        String s = TS_BRACKET.matcher(text).replaceAll("");
        s = TS_TOKEN.matcher(s).replaceAll("");
        s = SPEAKER_TAG.matcher(s).replaceAll("");
        return tidy(s);
    }

    private static String tidy(String s) {
        return s.replaceAll("[ \\t]{2,}", " ")
                .replaceAll(" *\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
