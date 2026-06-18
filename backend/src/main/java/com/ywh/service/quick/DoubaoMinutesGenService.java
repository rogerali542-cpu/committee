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
            你是业主委员会会议纪要助理。依据【会议转写】和【议题线索】，把口语化的讨论整理成规范、客观、书面化的会议纪要。

            硬性要求：
            1. 只依据转写内容，不得编造未出现的事实、人名、金额或决议。
            2. 「决议」字段只整理会上已形成的口径或讨论倾向，并在结尾注明"（待人工确认）"；切勿替与会者下最终表决结论。
            3. 语言简洁、正式，去掉口头语和重复；金额/数字/单位保持准确。
            4. 待办（todos）是会上明确要做的后续事项，没有就给空数组。
            5. 严格只输出一个 JSON 对象，不要任何解释或 Markdown 代码块包裹。

            输出 JSON 结构：
            {
              "topics": [
                {"ref": "议题标识(原样回填)", "summary": "讨论摘要", "resolution": "决议结论（待人工确认）", "todos": ["待办1","待办2"]}
              ],
              "minutesMarkdown": "整篇会议纪要的 Markdown（含标题、各议题小节、决议与待办）"
            }
            """;

    @Override
    public QuickPolishVO polish(Long meetingId, QuickExtractionVO extraction, AsrResult asr) {
        try {
            String userContent = buildUserContent(extraction, asr);
            String content = callLlm(userContent);
            QuickPolishVO vo = parse(meetingId, content);
            if (vo != null) return vo;
            log.warn("[MINUTES] 豆包返回无法解析为纪要 JSON，降级。meetingId={}", meetingId);
        } catch (Exception e) {
            log.error("[MINUTES] 豆包纪要生成失败，降级。meetingId=" + meetingId, e);
        }
        return fallback(meetingId, extraction);
    }

    // —— 组装给模型的用户内容 ——
    private String buildUserContent(QuickExtractionVO extraction, AsrResult asr) {
        StringBuilder sb = new StringBuilder();

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
        if (Boolean.TRUE.equals(t.getVoteRequired())) sb.append(" 需表决");
        if (t.getVoteHint() != null && t.getVoteHint().getResult() != null) {
            sb.append(" 表决倾向(仅参考)：").append(t.getVoteHint().getResult());
        }
        sb.append('\n');
        if (t.getSegmentMatches() != null) {
            for (QuickExtractionVO.SegmentMatch m : t.getSegmentMatches()) {
                if (m.getText() != null && !m.getText().isBlank()) {
                    sb.append("    · ").append(m.getText()).append('\n');
                }
            }
        }
    }

    // —— 调用方舟 chat/completions ——
    private String callLlm(String userContent) throws Exception {
        DoubaoProperties.Llm llm = props.getLlm();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", 0.3);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", userContent);
        body.putObject("response_format").put("type", "json_object");

        String url = trimTrailingSlash(llm.getBaseUrl()) + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
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
                topics.add(QuickPolishVO.TopicSummary.builder()
                        .ref(n.path("ref").asText(""))
                        .summary(n.path("summary").asText(""))
                        .resolution(n.path("resolution").asText(""))
                        .todos(todos)
                        .build());
            }
        }
        return QuickPolishVO.builder()
                .meetingId(meetingId)
                .topics(topics)
                .minutesMarkdown(root.path("minutesMarkdown").asText(""))
                .build();
    }

    // —— 调用失败/解析失败时的兜底：用规则层标题先撑住 UI ——
    private QuickPolishVO fallback(Long meetingId, QuickExtractionVO extraction) {
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
                .minutesMarkdown("## 会议纪要\n\n生成失败，已回退为规则层草稿，请人工整理。\n")
                .build();
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
