package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.dto.MeetingTodoVO;
import com.ywh.dto.MeetingTodoTicketVO;
import com.ywh.dto.RecordingVO;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import com.ywh.dto.quick.QuickConfirmRequest;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.QuickPolishVO;
import com.ywh.dto.quick.MinutesTaskStatusVO;
import com.ywh.dto.quick.TopicSummaryRequest;
import com.ywh.dto.quick.TopicSummaryTaskVO;
import com.ywh.service.CommitteeService;
import com.ywh.service.MeetingTodoTicketService;
import com.ywh.service.quick.AsrService;
import com.ywh.service.quick.AudioStorageService;
import com.ywh.service.quick.AudioTranscodeService;
import com.ywh.service.quick.MinutesGenService;
import com.ywh.service.quick.QuickExtractionService;
import com.ywh.service.quick.RecordingLiveService;
import com.ywh.service.quick.TopicSummaryTaskService;
import com.ywh.service.quick.TranscriptCorrectionService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/committees/{id}/quick")
@RequiredArgsConstructor
public class QuickMeetingController {

    private static final String[] QUICK_ROLES = {"主任", "副主任", "记录员", "委员"};
    private static final String[] CHAIR_ROLES = {"主任", "副主任"};

    private final AsrService asrService;
    private final TranscriptCorrectionService correctionService;
    private final QuickExtractionService extractionService;
    private final MinutesGenService minutesGenService;
    private final TopicSummaryTaskService topicSummaryTaskService;
    private final AudioStorageService audioStorage;
    private final CommitteeService committeeService;
    private final AudioTranscodeService audioTranscodeService;
    private final MeetingTodoTicketService meetingTodoTicketService;
    private final RecordingLiveService recordingLiveService;

    @PostMapping("/recording/upload")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Map<String, Object>> upload(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "durationSec", required = false) Integer durationSec) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("音频为空");
        }
        String ext = extractExt(file.getOriginalFilename());
        byte[] data = file.getBytes();
        // 豆包 bigasr.auc 只认 mp3/wav/ogg；浏览器 H5 录音多为 webm/mp4/m4a/aac，
        // 非友好格式统一转码为 16k 单声道 mp3（jave2 内置 ffmpeg），并把扩展名归一化为 mp3。
        boolean doubaoFriendly = ext != null
                && (ext.equals("mp3") || ext.equals("wav") || ext.equals("ogg"));
        if (!doubaoFriendly) {
            try {
                data = audioTranscodeService.toMono16kMp3(data, ext);
                ext = "mp3";
            } catch (Exception e) {
                return Result.fail("音频转码失败，请重试：" + e.getMessage());
            }
        }
        String url = audioStorage.save(id, data, ext);
        Long recordingId = committeeService.saveRecording(id, url,
                file.getOriginalFilename(), (long) data.length, durationSec);
        return Result.ok(Map.of(
                "recordingId", recordingId,
                "url", url,
                "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "",
                "fileSize", file.getSize()
        ));
    }

    /** 查询 ASR 转写任务状态（轮询接口） */
    @GetMapping("/recording/status")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrTaskVO> recordingStatus(@RequestParam String taskId) {
        return Result.ok(asrService.status(taskId));
    }

    /** 触发 ASR 转写。0722 用户定：委员上传的段也自动识别，故放开到全部会内角色。 */
    @PostMapping("/recordings/{recordingId}/transcribe")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrTaskVO> transcribeRecording(@PathVariable Long id,
                                                  @PathVariable Long recordingId) {
        return Result.ok(asrService.submit(id, recordingId, committeeService));
    }

    /** 获取会议全部录音列表 */
    @GetMapping("/recordings")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<List<RecordingVO>> recordings(@PathVariable Long id) {
        return Result.ok(committeeService.getRecordings(id));
    }

    // ── 「谁在录音」在册表：开录前查一下，避免两人同时录一段导致转写重复 ──

    /** 录音心跳（录音端每 10s 打一次；开始录音时立即打）。 */
    @PostMapping("/recording-live/beat")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Void> recordingBeat(@PathVariable Long id) {
        var ur = com.ywh.util.SecurityUtils.getCurrentUserRole();
        if (ur != null) recordingLiveService.beat(id, ur.getId(), ur.getRealName());
        return Result.ok(null);
    }

    /** 主动下线（暂停/停止/上传后）。丢心跳时由 25s TTL 兜底。 */
    @DeleteMapping("/recording-live/beat")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Void> recordingBeatStop(@PathVariable Long id) {
        var ur = com.ywh.util.SecurityUtils.getCurrentUserRole();
        if (ur != null) recordingLiveService.stop(id, ur.getId());
        return Result.ok(null);
    }

    /** 当前正在录音的人（roleId + name），前端排除自己后用于提示。 */
    @GetMapping("/recording-live")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<List<Map<String, Object>>> recordingLive(@PathVariable Long id) {
        return Result.ok(recordingLiveService.active(id));
    }

    /** 删除一条录音（转写页删废录/多余段）。仅主任/副主任可删。 */
    @DeleteMapping("/recordings/{recordingId}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> deleteRecording(@PathVariable Long id,
                                        @PathVariable Long recordingId) {
        committeeService.deleteRecording(id, recordingId);
        asrService.evictRecording(id, recordingId); // 清掉该段已缓存的转写结果，合并结果随之更新
        return Result.ok(null);
    }

    // ===== 以下接口保留，但 /recording/upload 已改为只存不转 =====

    @GetMapping("/transcript")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrResult> transcript(@PathVariable Long id) {
        return Result.ok(correctionService.correct(id, asrService.result(id)));
    }

    /**
     * 单条录音的转写原文（录音详情里「查看转写」用）。
     * 直接返回该段的 ASR 原文，不走 correctionService.correct —— 纠错缓存按 meetingId 存，
     * 传单段进去会覆盖整会合并稿的缓存。单段查看要的是"这条录音识别出了什么"，用原文即可；
     * 议题匹配/纪要仍以合并后 /transcript 的纠错稿为准。未转写返回 null（前端提示先转写）。
     */
    @GetMapping("/recordings/{recordingId}/transcript")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrResult> recordingTranscript(@PathVariable Long id,
                                                 @PathVariable Long recordingId) {
        return Result.ok(asrService.resultForRecording(id, recordingId));
    }

    @GetMapping("/extract")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<QuickExtractionVO> extract(@PathVariable Long id) {
        AsrResult asr = correctionService.correct(id, asrService.result(id));
        return Result.ok(extractionService.extract(id, asr));
    }

    @PostMapping("/topic-summary")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<String> topicSummary(@PathVariable Long id, @RequestBody TopicSummaryRequest req) {
        List<String> texts = topicTexts(id, req);
        String title = req != null ? req.getTitle() : null;
        String type = req != null ? req.getType() : null;
        return Result.ok(minutesGenService.summarizeTopic(title, type, texts));
    }

    @PostMapping("/topic-summary/tasks")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<TopicSummaryTaskVO> startTopicSummary(@PathVariable Long id, @RequestBody TopicSummaryRequest req) {
        List<String> texts = topicTexts(id, req);
        String title = req != null ? req.getTitle() : null;
        String type = req != null ? req.getType() : null;
        return Result.ok(topicSummaryTaskService.submit(id, title, type, texts));
    }

    @GetMapping("/topic-summary/tasks/{taskId}")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<TopicSummaryTaskVO> topicSummaryStatus(@PathVariable Long id, @PathVariable String taskId) {
        return Result.ok(topicSummaryTaskService.status(taskId));
    }

    @PostMapping("/confirm")
    @RequireRole({"主任", "副主任"})
    public Result<Void> confirm(@PathVariable Long id, @RequestBody QuickConfirmRequest req) {
        committeeService.applyQuickConfirm(id, req);
        return Result.ok();
    }

    // 生成仍是同步长请求（客户端断连后 servlet 线程照样跑完、纪要照落库）；这里额外把"生成中/完成/失败"
    // 落成任务状态，配合 GET /minutes-status，实现刷新/换设备/隔天重进也能查到进度、接回入口。
    @PostMapping("/polish")
    @RequireRole({"主任", "副主任"})
    public Result<QuickPolishVO> polish(@PathVariable Long id,
                                        @RequestBody(required = false) QuickConfirmRequest req) {
        committeeService.markMinutesTaskRunning(id);
        try {
            if (req != null) {
                committeeService.applyQuickConfirm(id, req);
            }
            AsrResult asr = correctionService.correct(id, asrService.result(id));
            QuickExtractionVO extraction = extractionService.extract(id, asr);
            String context = committeeService.buildQuickMinutesContextCompact(id, extraction, asr);
            QuickPolishVO vo = minutesGenService.polish(id, context, extraction, asr);
            boolean ok = vo.getMinutesMarkdown() != null && !vo.getMinutesMarkdown().isBlank();
            if (ok) {
                committeeService.updateQuickAiArtifacts(
                        id,
                        vo.getMinutesMarkdown(),
                        vo.getTopicReportMarkdown(),
                        vo.getTodoListMarkdown()
                );
            }
            // 纪要顺带提炼的现场意见入库（带"现场·AI"标，可认领）。失败不影响纪要本身。
            try {
                committeeService.saveAiOpinions(id, vo);
            } catch (Exception e) {
                log.warn("[MINUTES] AI 现场意见入库失败 meetingId={}: {}", id, e.getMessage());
            }
            committeeService.markMinutesTaskDone(id, ok, ok ? null : "生成结果为空");
            return Result.ok(vo);
        } catch (RuntimeException e) {
            committeeService.markMinutesTaskDone(id, false, e.getMessage());
            throw e;
        }
    }

    // 重进会议/刷新/换设备后查纪要生成进度：前端据此决定显示「生成中/查看/可重新生成」，不依赖前端内存。
    @GetMapping("/minutes-status")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<MinutesTaskStatusVO> minutesStatus(@PathVariable Long id) {
        return Result.ok(committeeService.getMinutesTaskStatus(id));
    }

    @GetMapping("/topic-report")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<String> topicReport(@PathVariable Long id) {
        return Result.ok(committeeService.getInternalTopicReport(id));
    }

    @GetMapping("/todos")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<String> todos(@PathVariable Long id) {
        return Result.ok(committeeService.getTodoListText(id));
    }

    /** 结构化待办列表。initialized=false 时附带 raw（AI 待办原文），供前端解析后固化。 */
    @GetMapping("/todos/list")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Map<String, Object>> todoList(@PathVariable Long id) {
        List<MeetingTodoVO> items = committeeService.listTodos(id);
        Map<String, Object> out = new HashMap<>();
        out.put("initialized", !items.isEmpty());
        out.put("items", items);
        out.put("raw", items.isEmpty() ? committeeService.getTodoListText(id) : null);
        return Result.ok(out);
    }

    /** 固化待办（前端解析 AI 文本后回传）。幂等，已固化则返回现有。 */
    @PostMapping("/todos/init")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<List<MeetingTodoVO>> todoInit(@PathVariable Long id, @RequestBody List<MeetingTodoVO> items) {
        return Result.ok(committeeService.initTodos(id, items));
    }

    /** 主任手动新增一条待办（AI 边界难界定，除识别外还需人工增补）。 */
    @PostMapping("/todos/add")
    @RequireRole({"主任", "副主任"})
    public Result<MeetingTodoVO> todoAdd(@PathVariable Long id, @RequestBody MeetingTodoVO item) {
        return Result.ok(committeeService.addTodo(id, item));
    }

    /** 委员更新某条待办状态：status = todo/doing/done。 */
    @PutMapping("/todos/{todoId}/status")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<MeetingTodoVO> todoStatus(@PathVariable Long id, @PathVariable Long todoId, @RequestParam String status) {
        return Result.ok(committeeService.updateTodoStatus(id, todoId, status));
    }

    /** 主任删除误识别、无需执行的待办。 */
    @DeleteMapping("/todos/{todoId}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> todoDelete(@PathVariable Long id, @PathVariable Long todoId) {
        committeeService.deleteTodo(id, todoId);
        return Result.ok();
    }

    /** 将一条会议待办推送到社区工单系统；外部单号稳定，重复点击由工单系统幂等返回。 */
    @PostMapping("/todos/{todoId}/ticket")
    @RequireRole({"主任", "副主任"})
    public Result<MeetingTodoTicketVO> todoTicket(@PathVariable Long id, @PathVariable Long todoId) {
        return Result.ok(meetingTodoTicketService.push(id, todoId));
    }

    private List<String> topicTexts(Long meetingId, TopicSummaryRequest req) {
        List<String> texts = requestSegmentTexts(req);
        if (!texts.isEmpty()) return texts;
        AsrResult asr = correctionService.correct(meetingId, asrService.result(meetingId));
        return topicContextTexts(asr, req);
    }

    private List<String> requestSegmentTexts(TopicSummaryRequest req) {
        List<String> texts = new ArrayList<>();
        if (req == null || req.getSegmentTexts() == null) return texts;
        for (String s : req.getSegmentTexts()) {
            if (texts.size() >= 36) break;
            if (s == null) continue;
            String text = s.trim();
            if (!text.isBlank()) texts.add(text);
        }
        return texts;
    }

    private List<String> topicContextTexts(AsrResult asr, TopicSummaryRequest req) {
        List<String> texts = new ArrayList<>();
        if (asr == null || asr.getSegments() == null || req == null || req.getSegmentIndexes() == null) {
            return texts;
        }
        int size = asr.getSegments().size();
        Set<Integer> picked = new LinkedHashSet<>();
        int min = size;
        int max = -1;
        for (Integer i : req.getSegmentIndexes()) {
            if (i != null && i >= 0 && i < size) {
                min = Math.min(min, i);
                max = Math.max(max, i);
            }
        }
        if (max >= min) {
            if (max - min <= 24) {
                for (int i = min; i <= max; i++) picked.add(i);
            }
            for (Integer i : req.getSegmentIndexes()) {
                if (i == null || i < 0 || i >= size) continue;
                for (int j = Math.max(0, i - 2); j <= Math.min(size - 1, i + 2); j++) {
                    picked.add(j);
                }
            }
        }
        for (Integer i : picked) {
            if (texts.size() >= 36) break;
            AsrResult.Segment seg = asr.getSegments().get(i);
            String speaker = seg.getSpeaker() == null ? "未知发言人" : seg.getSpeaker();
            String text = seg.getText() == null ? "" : seg.getText().trim();
            if (!text.isBlank()) texts.add(speaker + "：" + text);
        }
        return texts;
    }

    private String extractExt(String filename) {
        if (filename == null) return "mp3";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "mp3";
    }
}
