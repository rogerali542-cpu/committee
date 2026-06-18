package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import com.ywh.dto.quick.QuickConfirmRequest;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.QuickPolishVO;
import com.ywh.service.CommitteeService;
import com.ywh.service.quick.AsrService;
import com.ywh.service.quick.AudioStorageService;
import com.ywh.service.quick.MinutesGenService;
import com.ywh.service.quick.QuickExtractionService;
import com.ywh.service.quick.TranscriptCorrectionService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 快速会议模式（录音→纪要）后端框架。
 * 流程：录音上传 → 豆包 ASR(异步) → 规则层抽取 → 人工确认 → (按需)大模型整理 → 回填定稿。
 * 当前 ASR 与大模型均为桩，接入豆包只需替换对应 Service 实现。
 */
@RestController
@RequestMapping("/api/committees/{id}/quick")
@RequiredArgsConstructor
public class QuickMeetingController {

    private final AsrService asrService;
    private final TranscriptCorrectionService correctionService;
    private final QuickExtractionService extractionService;
    private final MinutesGenService minutesGenService;
    private final AudioStorageService audioStorage;
    private final CommitteeService committeeService;

    /**
     * 录音/音频上传（小程序录音 或 用户自选音频文件，二者都走这里）：
     * 保存音频 → 拿到豆包可拉取的 URL → 直接提交转写，返回 taskId。
     */
    @PostMapping("/recording/upload")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrTaskVO> upload(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("音频为空");
        }
        String ext = extractExt(file.getOriginalFilename());
        String url = audioStorage.save(id, file.getBytes(), ext);
        committeeService.saveRecordingUrl(id, url);   // 保留会议录音：存档地址落库
        return Result.ok(asrService.submit(id, url));
    }

    private String extractExt(String filename) {
        if (filename == null) return "mp3";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "mp3";
    }

    /** 1. 录音上传初始化：返回上传凭证（接入对象存储后给真实 uploadId/签名）。 */
    @PostMapping("/recording/init")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Map<String, Object>> initUpload(@PathVariable Long id) {
        // TODO 接入对象存储：返回真实的分片上传初始化信息（uploadId、分片地址/签名）。
        String uploadId = "upload_" + UUID.randomUUID().toString().substring(0, 8);
        return Result.ok(Map.of("uploadId", uploadId, "meetingId", id));
    }

    /** 2. 录音上传完成：触发异步转写，返回 taskId。audioRef = 对象存储 key。 */
    @PostMapping("/recording/complete")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<AsrTaskVO> completeUpload(@PathVariable Long id,
                                            @RequestParam(required = false) String audioRef) {
        return Result.ok(asrService.submit(id, audioRef));
    }

    /** 3. 轮询转写状态。 */
    @GetMapping("/recording/status")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrTaskVO> recordingStatus(@PathVariable Long id, @RequestParam String taskId) {
        return Result.ok(asrService.status(taskId));
    }

    /** 转写原文：返回结构化转写（说话人 + 时间 + 文本），供前端「转写原文」展示。 */
    @GetMapping("/transcript")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<AsrResult> transcript(@PathVariable Long id) {
        return Result.ok(correctionService.correct(id, asrService.result(id)));
    }

    /** 4. 规则层抽取：供「确认议题与表决」步骤渲染（预设议题 + 临时议题 + 表决弱提示）。 */
    @GetMapping("/extract")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<QuickExtractionVO> extract(@PathVariable Long id) {
        AsrResult asr = correctionService.correct(id, asrService.result(id));
        return Result.ok(extractionService.extract(id, asr));
    }

    /** 4.5 人工确认识别结果：将快速模式的汇总表决结果回填到会议记录。 */
    @PostMapping("/confirm")
    @RequireRole({"主任", "副主任"})
    public Result<Void> confirm(@PathVariable Long id, @RequestBody QuickConfirmRequest req) {
        committeeService.applyQuickConfirm(id, req);
        return Result.ok();
    }

    /** 认领"录音负责人"（任意已签到参会人）。 */
    @PostMapping("/recorder/claim")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Void> claimRecorder(@PathVariable Long id) {
        committeeService.claimRecorder(id);
        return Result.ok();
    }

    /** 重置"录音负责人"（异常兜底，仅主任/副主任）。 */
    @PostMapping("/recorder/reset")
    @RequireRole({"主任", "副主任"})
    public Result<Void> resetRecorder(@PathVariable Long id) {
        committeeService.resetRecorder(id);
        return Result.ok();
    }

    /** 5. 大模型整理（按需）：用户点「润色/生成纪要」时调用，返回结构化纪要。 */
    @PostMapping("/polish")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<QuickPolishVO> polish(@PathVariable Long id) {
        AsrResult asr = correctionService.correct(id, asrService.result(id));
        QuickExtractionVO extraction = extractionService.extract(id, asr);
        return Result.ok(minutesGenService.polish(id, extraction, asr));
    }
}
