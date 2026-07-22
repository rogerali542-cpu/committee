package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ywh.config.DoubaoProperties;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import com.ywh.entity.MeetingRecording;
import com.ywh.repository.MeetingRecordingRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private final MeetingRecordingRepository recordingRepo;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // meetingId -> 最近一次提交的请求 ID（taskId）
    private final ConcurrentHashMap<Long, String> meetingToReqId = new ConcurrentHashMap<>();
    // 请求 ID（taskId） -> meetingId
    private final ConcurrentHashMap<String, Long> reqIdToMeeting = new ConcurrentHashMap<>();
    // 请求 ID（taskId） -> recordingId（一条录音对应一次转写，用于按录音分别缓存以便合并）
    private final ConcurrentHashMap<String, Long> reqIdToRecording = new ConcurrentHashMap<>();
    // meetingId -> { recordingId -> 该条录音的识别结果 }。多条录音分别缓存，result() 时按 recordingId 升序合并。
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AsrResult>> recordingResults = new ConcurrentHashMap<>();
    // 正在后台向豆包提交（HTTP 请求尚未完成）的 reqId 集合
    private final Set<String> pendingReqIds = ConcurrentHashMap.newKeySet();
    // 后台提交失败的 reqId → 错误信息
    private final ConcurrentHashMap<String, String> submitErrors = new ConcurrentHashMap<>();

    @Override
    public AsrTaskVO submit(Long meetingId, String audioRef) {
        return submitInternal(meetingId, null, audioRef, null);
    }

    @Override
    public AsrTaskVO submit(Long meetingId, Long recordingId, CommitteeService committeeService) {
        String audioUrl = (recordingId != null && committeeService != null)
                ? committeeService.getRecordingUrl(recordingId) : null;
        return submitInternal(meetingId, recordingId, audioUrl, committeeService);
    }

    private AsrTaskVO submitInternal(Long meetingId, Long recordingId, String audioRef, CommitteeService cs) {
        DoubaoProperties.Asr a = props.getAsr();
        String reqId = UUID.randomUUID().toString();

        if (isBlank(a.getAppKey()) || isBlank(a.getAccessToken())) {
            return failed(reqId, meetingId, "DOUBAO_ASR_APP_KEY / DOUBAO_ASR_ACCESS_TOKEN not configured");
        }

        // ── 1. 同步构建 JSON 请求体（读文件 + Base64，快速本地操作） ──
        ObjectNode body = mapper.createObjectNode();
        body.putObject("user").put("uid", "ywh-" + meetingId);
        ObjectNode audioNode = body.putObject("audio");

        // 优先：后端本地存有该音频 → 直接内联 Base64 发送，豆包无需联网拉取
        //（兼容 localhost / 局域网 / 隧道等任意 public-base-url，彻底规避 45000006「下载音频失败」）。
        // 兜底：本地取不到（如对象存储直链）且 URL 公网可达 → 让豆包按 URL 拉取。
        // 远端对象存储(TOS)：音频不在本机、URL 公网可达 → 跳过内联，直接让豆包按 URL 拉，
        // 避免把长音频读回内存编码成几十 MB 的 Base64 body（提交超时/内存压力的根因）。
        byte[] audioBytes = null;
        String filename = extractFilename(audioRef);
        if (!audioStorage.isRemote() && filename != null) {
            try {
                audioBytes = audioStorage.load(filename);
            } catch (Exception e) {
                log.info("[ASRDBG] local audio not loadable file={} : {}", filename, e.getMessage());
            }
        }
        if (audioBytes != null && audioBytes.length > 0) {
            audioNode.put("data", java.util.Base64.getEncoder().encodeToString(audioBytes));
            audioNode.put("format", guessFormat(audioRef, a.getDefaultFormat()));
            log.info("[ASRDBG] submit via audio.data meetingId={} file={} size={} bytes", meetingId, filename, audioBytes.length);
        } else if (!isPrivateAudioUrl(audioRef)) {
            audioNode.put("url", audioRef);
            audioNode.put("format", guessFormat(audioRef, a.getDefaultFormat()));
            log.info("[ASRDBG] submit via audio.url meetingId={} audioUrl={}", meetingId, audioRef);
        } else {
            return failed(reqId, meetingId, "音频文件读取失败，请返回上一步重新上传录音");
        }

        ObjectNode req = body.putObject("request");
        req.put("model_name", a.getModelName());
        req.put("enable_itn", true);
        req.put("enable_punc", true);
        req.put("enable_speaker_info", true);
        req.put("show_utterances", true);

        // ── 2. 预注册 taskId，立即返回，HTTP 提交放后台 ──
        meetingToReqId.put(meetingId, reqId);
        reqIdToMeeting.put(reqId, meetingId);
        if (recordingId != null) reqIdToRecording.put(reqId, recordingId);
        pendingReqIds.add(reqId);
        markRecordingAsr(recordingId, "processing");

        final String bodyStr = body.toString();
        final Long mid = meetingId;
        final String rid = reqId;
        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> resp = post(a.getSubmitUrl(), rid, bodyStr);
                String code = resp.headers().firstValue("X-Api-Status-Code").orElse("");
                if (!"20000000".equals(code)) {
                    log.warn("[ASR] Doubao submit failed meetingId={} code={} body={}", mid, code, resp.body());
                    submitErrors.put(rid, "submit code=" + code);
                } else {
                    log.info("[ASR] Doubao submit OK meetingId={} reqId={}", mid, rid);
                }
            } catch (Exception e) {
                log.error("[ASR] Doubao submit exception meetingId={}", mid, e);
                submitErrors.put(rid, e.getMessage() != null ? e.getMessage() : "网络异常");
            } finally {
                pendingReqIds.remove(rid);
            }
        });

        return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId).status("processing").build();
    }

    private AsrTaskVO failed(String reqId, Long meetingId, String message) {
        return AsrTaskVO.builder().taskId(reqId).meetingId(meetingId).status("failed").message(message).build();
    }

    @Override
    public AsrTaskVO status(String taskId) {
        Long meetingId = reqIdToMeeting.get(taskId);
        Long recId = reqIdToRecording.get(taskId);
        // 后台提交尚未完成，不能查豆包（reqId 还未登记到豆包侧）
        if (pendingReqIds.contains(taskId)) {
            return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId).status("processing").build();
        }
        // 后台提交失败
        String submitErr = submitErrors.get(taskId);
        if (submitErr != null) {
            markRecordingAsr(recId, "failed");
            return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId).status("failed").message(submitErr).build();
        }
        // 正常查询豆包
        try {
            HttpResponse<String> resp = post(props.getAsr().getQueryUrl(), taskId, "{}");
            String code = resp.headers().firstValue("X-Api-Status-Code").orElse("");
            // 静音/无有效语音：豆包返回 20000003，这是【终态】而非处理中。
            // 但 mapStatus 的 startsWith("2000000") 会把它误判为 processing，导致前端永远停在"录音转写中"。
            // 这里显式当作"已完成但结果为空"，返回 done + 空结果，让前端走"未检测到有效语音"的提示。
            if ("20000003".equals(code)) {
                if (meetingId != null) {
                    putRecordingResult(meetingId, recId, AsrResult.builder()
                            .meetingId(meetingId).durationSec(0)
                            .segments(new ArrayList<>()).build());
                }
                markRecordingAsr(recId, "done"); // 静音也算已处理（并入合并，贡献 0 段）
                log.info("[ASR] no valid speech (silence) code=20000003 meetingId={}", meetingId);
                return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId).status("done").build();
            }
            String status = mapStatus(code);
            if (!"processing".equals(status) && !"done".equals(status)) {
                String msg = resp.headers().firstValue("X-Api-Message").orElse("");
                log.warn("[ASRDBG] query failed code={} X-Api-Message={} body={}", code, msg, resp.body());
            }
            if ("done".equals(status) && meetingId != null) {
                putRecordingResult(meetingId, recId, parse(meetingId, resp.body()));
                markRecordingAsr(recId, "done");
            } else if ("failed".equals(status)) {
                markRecordingAsr(recId, "failed");
            }
            return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId)
                    .status(status).message("done".equals(status) ? null : asrMessage(code, resp)).build();
        } catch (Exception e) {
            log.error("Doubao ASR query exception", e);
            markRecordingAsr(recId, "failed");
            return AsrTaskVO.builder().taskId(taskId).meetingId(meetingId)
                    .status("failed").message(e.getMessage()).build();
        }
    }

    @Override
    public AsrResult result(Long meetingId) {
        hydrateFromDb(meetingId); // 服务重启后内存为空：先把已落库的各段转写捞回来
        ConcurrentHashMap<Long, AsrResult> perRec = recordingResults.get(meetingId);
        if (perRec == null || perRec.isEmpty()) {
            // 懒触发：最近一次任务可能已 done 但尚未缓存（status() 在 done 时写入 recordingResults）
            String reqId = meetingToReqId.get(meetingId);
            if (reqId == null) return null;
            AsrTaskVO t = status(reqId);
            if (!"done".equals(t.getStatus())) return null;
            perRec = recordingResults.get(meetingId);
            if (perRec == null || perRec.isEmpty()) return null;
        }
        return mergeRecordings(meetingId, perRec);
    }

    @Override
    public AsrResult resultForRecording(Long meetingId, Long recordingId) {
        if (meetingId == null || recordingId == null) return null;
        ConcurrentHashMap<Long, AsrResult> perRec = recordingResults.get(meetingId);
        AsrResult hit = perRec == null ? null : perRec.get(recordingId);
        if (hit != null) return hit;
        // 内存没有（服务重启过）→ 从库里恢复
        AsrResult fromDb = loadAsrFromDb(recordingId);
        if (fromDb != null) {
            recordingResults.computeIfAbsent(meetingId, k -> new ConcurrentHashMap<>()).put(recordingId, fromDb);
        }
        return fromDb;
    }

    /** 把该会议所有已落库的单段转写结果补进内存缓存（重启后合并稿仍可用）。已在内存的不覆盖。 */
    private void hydrateFromDb(Long meetingId) {
        if (meetingId == null) return;
        try {
            for (MeetingRecording rec : recordingRepo.findByMeetingIdOrderByCreatedAtDesc(meetingId)) {
                if (rec.getAsrJson() == null || rec.getAsrJson().isBlank()) continue;
                ConcurrentHashMap<Long, AsrResult> perRec =
                        recordingResults.computeIfAbsent(meetingId, k -> new ConcurrentHashMap<>());
                if (perRec.containsKey(rec.getId())) continue;
                AsrResult r = loadAsrFromDb(rec.getId());
                if (r != null) perRec.put(rec.getId(), r);
            }
        } catch (Exception e) {
            log.warn("[ASR] 从库恢复转写缓存失败 meetingId={}: {}", meetingId, e.getMessage());
        }
    }

    private AsrResult loadAsrFromDb(Long recordingId) {
        try {
            MeetingRecording rec = recordingRepo.findById(recordingId).orElse(null);
            if (rec == null || rec.getAsrJson() == null || rec.getAsrJson().isBlank()) return null;
            return mapper.readValue(rec.getAsrJson(), AsrResult.class);
        } catch (Exception e) {
            log.warn("[ASR] 解析已落库的转写结果失败 recordingId={}: {}", recordingId, e.getMessage());
            return null;
        }
    }

    @Override
    public void evictRecording(Long meetingId, Long recordingId) {
        ConcurrentHashMap<Long, AsrResult> perRec = recordingResults.get(meetingId);
        if (perRec != null && recordingId != null) perRec.remove(recordingId);
    }

    /** 缓存某条录音的识别结果。recordingId 为空（旧两参路径）时归入合并槽 -1。 */
    private void putRecordingResult(Long meetingId, Long recordingId, AsrResult r) {
        long key = recordingId != null ? recordingId : -1L;
        recordingResults.computeIfAbsent(meetingId, k -> new ConcurrentHashMap<>()).put(key, r);
        // 同步落库：重启后单段查看/合并稿都能恢复。失败仅记日志，不影响识别主流程。
        if (recordingId != null) {
            try {
                MeetingRecording rec = recordingRepo.findById(recordingId).orElse(null);
                if (rec != null) {
                    rec.setAsrJson(mapper.writeValueAsString(r));
                    recordingRepo.save(rec);
                }
            } catch (Exception e) {
                log.warn("[ASR] 转写结果落库失败 recordingId={}: {}", recordingId, e.getMessage());
            }
        }
    }

    /** 落库该录音的转写状态（none/processing/done/failed），供前端列表展示与多选去重。容错：失败仅记日志。 */
    private void markRecordingAsr(Long recordingId, String status) {
        if (recordingId == null || status == null) return;
        try {
            MeetingRecording r = recordingRepo.findById(recordingId).orElse(null);
            if (r != null && !status.equals(r.getAsrStatus())) {
                r.setAsrStatus(status);
                recordingRepo.save(r);
            }
        } catch (Exception e) {
            log.warn("[ASR] 更新录音 asrStatus 失败 recordingId={} status={}: {}", recordingId, status, e.getMessage());
        }
    }

    /**
     * 把一次会议的多条录音转写结果合并为一份：按 recordingId 升序拼接（保证段顺序稳定，
     * 议题匹配的 segmentIndex 依赖此顺序），后一条录音的时间轴接在前一条之后累加偏移。
     * 仅一条时等价于原结果。
     */
    private AsrResult mergeRecordings(Long meetingId, Map<Long, AsrResult> perRec) {
        List<Long> keys = new ArrayList<>(perRec.keySet());
        keys.sort(Comparator.naturalOrder());
        List<AsrResult.Segment> merged = new ArrayList<>();
        long offset = 0;
        for (Long k : keys) {
            AsrResult r = perRec.get(k);
            if (r == null || r.getSegments() == null) continue;
            long maxEnd = 0;
            for (AsrResult.Segment s : r.getSegments()) {
                merged.add(AsrResult.Segment.builder()
                        .speaker(s.getSpeaker())
                        .startMs(s.getStartMs() + offset)
                        .endMs(s.getEndMs() + offset)
                        .text(s.getText())
                        .build());
                maxEnd = Math.max(maxEnd, s.getEndMs());
            }
            offset += maxEnd; // 下一条录音接在当前录音末尾之后
        }
        return AsrResult.builder()
                .meetingId(meetingId)
                .durationSec((int) (offset / 1000))
                .segments(merged)
                .build();
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

    /** 稳健提取文件名（兼容无 scheme、相对路径、裸文件名、带 query/fragment），
     *  如 http://host:8080/api/quick-audio/10_abc.mp3 → 10_abc.mp3 */
    private String extractFilename(String audioRef) {
        if (audioRef == null || audioRef.isBlank()) return null;
        String s = audioRef;
        int q = s.indexOf('?'); if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#'); if (h >= 0) s = s.substring(0, h);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        int slash = s.lastIndexOf('/');
        String name = slash >= 0 ? s.substring(slash + 1) : s;
        return name.isBlank() ? null : name;
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
