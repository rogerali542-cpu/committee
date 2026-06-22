package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ywh.config.DoubaoProperties;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 豆包「录音文件识别大模型-标准版」(volc.bigasr.auc) 接入实现。
 * 异步：submit 提交音频URL → 拿 X-Api-Request-Id 作为任务句柄 → query 轮询拿结果。
 * 鉴权走 header：X-Api-App-Key / X-Api-Access-Key / X-Api-Resource-Id。
 *
 * 仅当 doubao.asr.enabled=true 时启用（否则用 AsrServiceStub 的演示转写）。
 *
 * ⚠️ 请求/响应字段名以火山引擎控制台文档为准——下方按标准版常见结构实现，
 *    若你的开通版本字段不同（如 utterances 内 speaker/start_time 的命名），调 parse* 即可。
 *    另：标准版需要音频是火山可访问的 URL（audioRef 传 TOS/OSS 直链）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "doubao.asr", name = "enabled", havingValue = "true")
public class DoubaoAsrService implements AsrService {

    private final DoubaoProperties props;
    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // meetingId -> 火山请求ID(任务句柄)
    private final ConcurrentHashMap<Long, String> meetingToReqId = new ConcurrentHashMap<>();
    // 火山请求ID -> meetingId
    private final ConcurrentHashMap<String, Long> reqIdToMeeting = new ConcurrentHashMap<>();
    // meetingId -> 解析后的转写（done 后缓存）
    private final ConcurrentHashMap<Long, AsrResult> results = new ConcurrentHashMap<>();

    @Override
    public AsrTaskVO submit(Long meetingId, String audioRef) {
        DoubaoProperties.Asr a = props.getAsr();
        String reqId = UUID.randomUUID().toString();
        try {
            if (isBlank(a.getAppKey()) || isBlank(a.getAccessToken())) {
                return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                        .status("failed").message("DOUBAO_ASR_APP_KEY / DOUBAO_ASR_ACCESS_TOKEN 未配置").build();
            }
            if (isPrivateAudioUrl(audioRef)) {
                return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                        .status("failed").message("音频地址不是公网地址，豆包无法下载；请配置对象存储或公网 STORAGE_PUBLIC_BASE_URL").build();
            }
            ObjectNode body = mapper.createObjectNode();
            body.putObject("user").put("uid", "ywh-" + meetingId);
            body.putObject("audio")
                    .put("url", audioRef)
                    .put("format", guessFormat(audioRef, a.getDefaultFormat()));
            ObjectNode req = body.putObject("request");
            req.put("model_name", a.getModelName());
            req.put("enable_itn", true);          // 数字规整
            req.put("enable_punc", true);         // 标点
            req.put("enable_speaker_info", true); // 说话人分离
            req.put("show_utterances", true);

            log.info("[ASRDBG] submit meetingId={} audioUrl={}", meetingId, audioRef);
            HttpResponse<String> resp = post(a.getSubmitUrl(), reqId, body.toString());
            String code = resp.headers().firstValue("X-Api-Status-Code").orElse("");
            if (!"20000000".equals(code)) {
                log.warn("豆包ASR submit 失败 code={} body={}", code, resp.body());
                return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                        .status("failed").message("submit code=" + code).build();
            }
            meetingToReqId.put(meetingId, reqId);
            reqIdToMeeting.put(reqId, meetingId);
            return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId).status("processing").build();
        } catch (Exception e) {
            log.error("豆包ASR submit 异常", e);
            return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                    .status("failed").message(e.getMessage()).build();
        }
    }

    @Override
    public AsrTaskVO status(String taskId) {
        Long meetingId = reqIdToMeeting.get(taskId);
        try {
            HttpResponse<String> resp = post(props.getAsr().getQueryUrl(), taskId, "{}");
            String code = resp.headers().firstValue("X-Api-Status-Code").orElse("");
            String status = mapStatus(code);
            if (!"processing".equals(status) && !"done".equals(status)) {
                String msg = resp.headers().firstValue("X-Api-Message").orElse("");
                log.warn("[ASRDBG] query failed code={} X-Api-Message={} body={}", code, msg, resp.body());
            }
            if ("done".equals(status) && meetingId != null) {
                results.put(meetingId, parse(meetingId, resp.body()));
            }
            return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId)
                    .status(status).message("done".equals(status) ? null : "code=" + code).build();
        } catch (Exception e) {
            log.error("豆包ASR query 异常", e);
            return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId)
                    .status("failed").message(e.getMessage()).build();
        }
    }

    @Override
    public AsrResult result(Long meetingId) {
        AsrResult cached = results.get(meetingId);
        if (cached != null) return cached;
        // 未缓存：尝试按已存的请求ID查询一次
        String reqId = meetingToReqId.get(meetingId);
        if (reqId == null) return null;
        AsrTaskVO t = status(reqId);
        return "done".equals(t.getStatus()) ? results.get(meetingId) : null;
    }

    // —— HTTP ——
    private HttpResponse<String> post(String url, String reqId, String json) throws Exception {
        DoubaoProperties.Asr a = props.getAsr();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Api-App-Key", a.getAppKey())
                .header("X-Api-Access-Key", a.getAccessToken())
                .header("X-Api-Resource-Id", a.getResourceId())
                .header("X-Api-Request-Id", reqId)
                .header("X-Api-Sequence", "-1")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String mapStatus(String code) {
        if ("20000000".equals(code)) return "done";
        if (code != null && code.startsWith("2000000")) return "processing"; // 排队/处理中
        return "failed";
    }

    private String guessFormat(String audioRef, String fallback) {
        if (audioRef == null) return fallback;
        int dot = audioRef.lastIndexOf('.');
        if (dot < 0 || dot == audioRef.length() - 1) return fallback;
        String ext = audioRef.substring(dot + 1).toLowerCase();
        int q = ext.indexOf('?');
        if (q >= 0) ext = ext.substring(0, q);
        return ext.isEmpty() ? fallback : ext;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPrivateAudioUrl(String audioRef) {
        if (audioRef == null || audioRef.isBlank()) return true;
        try {
            String host = URI.create(audioRef).getHost();
            if (host == null) return true;
            host = host.toLowerCase();
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || host.startsWith("192.168.")) return true;
            if (host.startsWith("10.")) return true;
            if (host.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*")) return true;
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    // —— 解析转写结果（字段名以控制台文档为准，必要时改这里）——
    private AsrResult parse(Long meetingId, String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode result = root.path("result");
        List<AsrResult.Segment> segments = new ArrayList<>();
        JsonNode utterances = result.path("utterances");
        long maxEnd = 0;
        if (utterances.isArray()) {
            for (JsonNode u : utterances) {
                long start = u.path("start_time").asLong(0);
                long end = u.path("end_time").asLong(0);
                // 说话人字段：标准版多在 additions.speaker 或 speaker，做兼容
                String speaker = u.path("speaker").asText(
                        u.path("additions").path("speaker").asText("S0"));
                if (!speaker.startsWith("S")) speaker = "S" + speaker;
                segments.add(AsrResult.Segment.builder()
                        .speaker(speaker).startMs(start).endMs(end)
                        .text(u.path("text").asText("")).build());
                maxEnd = Math.max(maxEnd, end);
            }
        }
        return AsrResult.builder()
                .meetingId(meetingId)
                .durationSec((int) (maxEnd / 1000))
                .segments(segments)
                .build();
    }
}
