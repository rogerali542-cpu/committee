package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ywh.config.DoubaoProperties;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import com.ywh.service.CommitteeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 火山引擎豆包语音识别（极速版 volc.bigasr.auc）对接实现。
 * submit 提交音频 URL 并返回 taskId，query 轮询任务状态；同一任务通过 X-Api-Request-Id 关联。
 * 鉴权 header 为 X-Api-App-Key / X-Api-Access-Key / X-Api-Resource-Id。
 * 仅当 doubao.asr.enabled=true 时启用本实现，否则由 AsrServiceStub 兜底；audioRef 必须公网可达（如 TOS/OSS）。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "doubao.asr", name = "enabled", havingValue = "true")
public class DoubaoAsrService implements AsrService {

    private final DoubaoProperties props;
    private final ObjectMapper mapper;
    private final AudioStorageService audioStorage;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // meetingId -> 最近一次提交的请求 ID（taskId）
    private final ConcurrentHashMap<Long, String> meetingToReqId = new ConcurrentHashMap<>();
    // 请求 ID（taskId） -> meetingId
    private final ConcurrentHashMap<String, Long> reqIdToMeeting = new ConcurrentHashMap<>();
    // meetingId -> 已解析完成的识别结果（任务 done 后缓存，供后续读取）
    private final ConcurrentHashMap<Long, AsrResult> results = new ConcurrentHashMap<>();

    @Override
    public AsrTaskVO submit(Long meetingId, String audioRef) {
        return submitInternal(meetingId, null, audioRef);
    }

    @Override
    public AsrTaskVO submit(Long meetingId, Long recordingId, CommitteeService committeeService) {
        String audioUrl = null;
        if (recordingId != null && committeeService != null) {
            audioUrl = committeeService.getRecordingUrl(recordingId);
        }
        return submitInternal(meetingId, recordingId, audioUrl);
    }

    private AsrTaskVO submitInternal(Long meetingId, Long recordingId, String audioRef) {
        DoubaoProperties.Asr a = props.getAsr();
        String reqId = UUID.randomUUID().toString();
        try {
            if (isBlank(a.getAppKey()) || isBlank(a.getAccessToken())) {
                return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                        .status("failed").message("DOUBAO_ASR_APP_KEY / DOUBAO_ASR_ACCESS_TOKEN not configured").build();
            }

            ObjectNode body = mapper.createObjectNode();
            body.putObject("user").put("uid", "ywh-" + meetingId);
            ObjectNode audioNode = body.putObject("audio");

            // 优先尝试用本地上传文件 Base64 提交（避免公网 URL 问题）
            if (isPrivateAudioUrl(audioRef)) {
                String filename = extractFilename(audioRef);
                if (filename != null) {
                    try {
                        byte[] audioBytes = audioStorage.load(filename);
                        String b64 = java.util.Base64.getEncoder().encodeToString(audioBytes);
                        audioNode.put("data", b64);
                        audioNode.put("format", guessFormat(audioRef, a.getDefaultFormat()));
                        log.info("[ASRDBG] submit via audio.data meetingId={} file={} size={} bytes", meetingId, filename, audioBytes.length);
                    } catch (Exception e) {
                        log.warn("[ASRDBG] unable to load audio file for base64 submit: {}", e.getMessage());
                        return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                                .status("failed").message("音频文件读取失败，请重新上传").build();
                    }
                } else {
                    return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                            .status("failed").message("音频公网地址无法访问，请返回上一步重新上传录音").build();
                }
            } else {
                audioNode.put("url", audioRef);
                audioNode.put("format", guessFormat(audioRef, a.getDefaultFormat()));
                log.info("[ASRDBG] submit via audio.url meetingId={} audioUrl={}", meetingId, audioRef);
            }

            ObjectNode req = body.putObject("request");
            req.put("model_name", a.getModelName());
            req.put("enable_itn", true);
            req.put("enable_punc", true);
            req.put("enable_speaker_info", true);
            req.put("show_utterances", true);

            HttpResponse<String> resp = post(a.getSubmitUrl(), reqId, body.toString());
            String code = resp.headers().firstValue("X-Api-Status-Code").orElse("");
            if (!"20000000".equals(code)) {
                log.warn("Doubao ASR submit failed code={} body={}", code, resp.body());
                return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId)
                        .status("failed").message("submit code=" + code).build();
            }
            meetingToReqId.put(meetingId, reqId);
            reqIdToMeeting.put(reqId, meetingId);
            return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId).status("processing").build();
        } catch (Exception e) {
            log.error("Doubao ASR submit exception", e);
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
                    .status(status).message("done".equals(status) ? null : asrMessage(code, resp)).build();
        } catch (Exception e) {
            log.error("Doubao ASR query exception", e);
            return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId)
                    .status("failed").message(e.getMessage()).build();
        }
    }

    @Override
    public AsrResult result(Long meetingId) {
        AsrResult cached = results.get(meetingId);
        if (cached != null) return cached;
        String reqId = meetingToReqId.get(meetingId);
        if (reqId == null) return null;
        AsrTaskVO t = status(reqId);
        return "done".equals(t.getStatus()) ? results.get(meetingId) : null;
    }

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
        if (code != null && code.startsWith("2000000")) return "processing";
        return "failed";
    }


    private String asrMessage(String code, HttpResponse<String> resp) {
        String raw = resp.headers().firstValue("X-Api-Message").orElse("");
        if ("45000006".equals(code)) {
            return "音频下载或解析失败，请确认音频 URL 公网可达且格式受支持，code=" + code;
        }
        if (raw != null && !raw.isBlank()) {
            return raw + "，code=" + code;
        }
        return "ASR 任务失败，code=" + code;
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

    /** 从 URL 提取文件名，如 /api/quick-audio/10_abc.mp3 → 10_abc.mp3 */
    private String extractFilename(String audioRef) {
        if (audioRef == null) return null;
        try {
            String path = URI.create(audioRef).getPath();
            if (path == null) return null;
            int slash = path.lastIndexOf('/');
            return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : null;
        } catch (Exception e) {
            return null;
        }
    }

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
                // 优先取 speaker 字段，缺失时回退到 additions.speaker，默认 S0
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
