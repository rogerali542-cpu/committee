package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ywh.config.DoubaoProperties;
import com.ywh.dto.quick.AsrResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 豆包大模型「保结构纠错」：逐段只改字，speaker / 时间戳 / 段数全部保留。
 * 仅当 doubao.llm.enabled=true 时启用。
 *
 * 安全策略：
 *  - 让模型按 index 逐段返回纠正后文本；
 *  - 数量/索引校验：返回段数必须 == 原段数、index 集合必须完整，否则【整批回退原文】；
 *  - 单段纠正文本为空时回退该段原文；
 *  - 调用异常一律回退原文。宁可不纠错，也不接受错位/丢段的结果污染下游。
 *  - 结果按会议内容签名缓存，避免 transcript/extract/polish 多次触发重复调用。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "doubao.llm", name = "enabled", havingValue = "true")
public class DoubaoTranscriptCorrectionService implements TranscriptCorrectionService {

    private final DoubaoProperties props;
    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // meetingId -> 已纠错结果（带源签名，源变了则重算）
    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(int signature, AsrResult result) {}

    private static final String SYSTEM_PROMPT = """
            你是中文会议语音转写纠错助手。给你一组按 index 排列的转写片段，只纠正语音识别的明显错误（同音字、错别字、缺失或错误的标点、明显误识的专业术语）。

            严格规则：
            1. 不得改变 index，不得增加或删除片段；输出片段数必须与输入完全一致，逐条一一对应。
            2. 不得合并或拆分片段，不得调整顺序。
            3. 不得改动金额、数字、日期、人名（除非上下文明确是同音误识）；不得增删语义内容，不得润色改写。
            4. 无需纠正的片段，原样返回其文本。
            5. 只输出一个 JSON 对象：{"segments":[{"i":0,"text":"..."},{"i":1,"text":"..."}]}，不要解释、不要 Markdown 代码块。
            """;

    @Override
    public AsrResult correct(Long meetingId, AsrResult asr) {
        if (asr == null || asr.getSegments() == null || asr.getSegments().isEmpty()) return asr;

        int sig = signature(asr);
        Cached hit = cache.get(meetingId);
        if (hit != null && hit.signature() == sig) return hit.result();

        AsrResult result;
        try {
            result = doCorrect(asr);
        } catch (Exception e) {
            log.error("[CORRECT] 纠错失败，回退原文。meetingId=" + meetingId, e);
            result = asr;
        }
        cache.put(meetingId, new Cached(sig, result));
        return result;
    }

    private AsrResult doCorrect(AsrResult asr) throws Exception {
        List<AsrResult.Segment> src = asr.getSegments();

        // 组请求：{"segments":[{"i":0,"text":"..."}, ...]}
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode arr = payload.putArray("segments");
        for (int i = 0; i < src.size(); i++) {
            arr.addObject().put("i", i).put("text", nullToEmpty(src.get(i).getText()));
        }

        String content = callLlm(payload.toString());

        Map<Integer, String> corrected = parseCorrections(content);
        // —— 数量 + 索引校验：不满足则整批回退 ——
        if (corrected.size() != src.size()) {
            log.warn("[CORRECT] 返回段数 {} != 原段数 {}，整批回退原文", corrected.size(), src.size());
            return asr;
        }
        for (int i = 0; i < src.size(); i++) {
            if (!corrected.containsKey(i)) {
                log.warn("[CORRECT] 缺少 index={} 的片段，整批回退原文", i);
                return asr;
            }
        }

        List<AsrResult.Segment> out = new ArrayList<>(src.size());
        for (int i = 0; i < src.size(); i++) {
            AsrResult.Segment s = src.get(i);
            String fixed = corrected.get(i);
            out.add(AsrResult.Segment.builder()
                    .speaker(s.getSpeaker())
                    .startMs(s.getStartMs())
                    .endMs(s.getEndMs())
                    .text(fixed == null || fixed.isBlank() ? s.getText() : fixed)   // 空则回退该段原文
                    .build());
        }
        return AsrResult.builder()
                .meetingId(asr.getMeetingId())
                .durationSec(asr.getDurationSec())
                .segments(out)
                .build();
    }

    private Map<Integer, String> parseCorrections(String content) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        if (content == null || content.isBlank()) return map;
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) return map;
        JsonNode root = mapper.readTree(content.substring(start, end + 1));
        JsonNode segs = root.path("segments");
        if (segs.isArray()) {
            for (JsonNode n : segs) {
                if (!n.hasNonNull("i")) continue;
                map.put(n.path("i").asInt(), n.path("text").asText(""));
            }
        }
        return map;
    }

    // —— 调用方舟 chat/completions ——
    private String callLlm(String userContent) throws Exception {
        DoubaoProperties.Llm llm = props.getLlm();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", 0);
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

    private int signature(AsrResult asr) {
        StringBuilder sb = new StringBuilder();
        for (AsrResult.Segment s : asr.getSegments()) {
            sb.append(s.getSpeaker()).append('|')
              .append(s.getStartMs()).append('|')
              .append(nullToEmpty(s.getText())).append('\n');
        }
        return sb.toString().hashCode();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
