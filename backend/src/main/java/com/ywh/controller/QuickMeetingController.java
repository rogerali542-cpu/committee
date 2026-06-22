package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import com.ywh.dto.quick.QuickConfirmRequest;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.QuickPolishVO;
import com.ywh.dto.quick.TopicSummaryRequest;
import com.ywh.dto.quick.TopicSummaryTaskVO;
import com.ywh.service.CommitteeService;
import com.ywh.service.quick.AsrService;
import com.ywh.service.quick.AudioStorageService;
import com.ywh.service.quick.MinutesGenService;
import com.ywh.service.quick.QuickExtractionService;
import com.ywh.service.quick.TopicSummaryTaskService;
import com.ywh.service.quick.TranscriptCorrectionService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    @PostMapping("/recording/upload")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrTaskVO> upload(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("音频为空");
        }
        String ext = extractExt(file.getOriginalFilename());
        String url = audioStorage.save(id, file.getBytes(), ext);
        committeeService.saveRecordingUrl(id, url);
        return Result.ok(asrService.submit(id, url));
    }

    @PostMapping("/recording/init")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Map<String, Object>> initUpload(@PathVariable Long id) {
        String uploadId = "upload_" + UUID.randomUUID().toString().substring(0, 8);
        return Result.ok(Map.of("uploadId", uploadId, "meetingId", id));
    }

    @PostMapping("/recording/complete")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<AsrTaskVO> completeUpload(@PathVariable Long id,
                                            @RequestParam(required = false) String audioRef) {
        return Result.ok(asrService.submit(id, audioRef));
    }

    @GetMapping("/recording/status")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrTaskVO> recordingStatus(@PathVariable Long id, @RequestParam String taskId) {
        return Result.ok(asrService.status(taskId));
    }

    @GetMapping("/transcript")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrResult> transcript(@PathVariable Long id) {
        return Result.ok(correctionService.correct(id, asrService.result(id)));
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

    @PostMapping("/recorder/claim")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Void> claimRecorder(@PathVariable Long id) {
        committeeService.claimRecorder(id);
        return Result.ok();
    }

    @PostMapping("/recorder/reset")
    @RequireRole({"主任", "副主任"})
    public Result<Void> resetRecorder(@PathVariable Long id) {
        committeeService.resetRecorder(id);
        return Result.ok();
    }

    @PostMapping("/polish")
    @RequireRole({"主任", "副主任"})
    public Result<QuickPolishVO> polish(@PathVariable Long id,
                                        @RequestBody(required = false) QuickConfirmRequest req) {
        if (req != null) {
            committeeService.applyQuickConfirm(id, req);
        }
        AsrResult asr = correctionService.correct(id, asrService.result(id));
        QuickExtractionVO extraction = extractionService.extract(id, asr);
        String context = committeeService.buildQuickMinutesContextCompact(id, extraction, asr);
        QuickPolishVO vo = minutesGenService.polish(id, context, extraction, asr);
        if (vo.getMinutesMarkdown() != null && !vo.getMinutesMarkdown().isBlank()) {
            committeeService.updateQuickAiArtifacts(
                    id,
                    vo.getMinutesMarkdown(),
                    vo.getTopicReportMarkdown(),
                    vo.getTodoListMarkdown()
            );
        }
        return Result.ok(vo);
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
