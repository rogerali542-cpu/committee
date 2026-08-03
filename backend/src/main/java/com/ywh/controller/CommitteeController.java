package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.annotation.RequirePermission;
import com.ywh.enums.SystemPermission;
import com.ywh.dto.CreateMeetingRequest;
import com.ywh.dto.DeliverySendRequest;
import com.ywh.dto.MeetingDetailVO;
import com.ywh.dto.MeetingTodoVO;
import com.ywh.dto.MeetingPrefillVO;
import com.ywh.dto.OnlineAttendanceRequest;
import com.ywh.dto.quick.NewsTaskStatusVO;
import com.ywh.dto.ProxyActionRequest;
import com.ywh.dto.ProxyTargetVO;
import com.ywh.entity.CommitteeMeeting;
import com.ywh.entity.RecordTopic;
import com.ywh.service.CommitteeService;
import com.ywh.service.AttendanceSheetPdfService;
import com.ywh.service.MeetingRecordPdfService;
import com.ywh.service.quick.AudioStorageService;
import com.ywh.service.quick.DocumentPrefillService;
import com.ywh.service.quick.NewsAsyncWorker;
import com.ywh.service.quick.NewsTaskService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/committees")
@RequiredArgsConstructor
public class CommitteeController {

    private final CommitteeService service;
    private final AttendanceSheetPdfService attendanceSheetPdfService;
    private final MeetingRecordPdfService meetingRecordPdfService;
    private final DocumentPrefillService prefillService;
    private final NewsTaskService newsTaskService;
    private final NewsAsyncWorker newsAsyncWorker;
    private final AudioStorageService audioStorage;

    @GetMapping
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Boolean archived) {
        return Result.ok(service.listMeetings(stage, archived));
    }

    /** 业委会整体待办汇总（0730 独立待办页）：跨会议聚合，不再绑定单场会议。
     *  字面量路径 /todos/overview 不会与 /{id}/** 冲突（Spring 精确段优先于变量段）。 */
    @GetMapping("/todos/overview")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<List<MeetingTodoVO>> todosOverview() {
        return Result.ok(service.listAllTodos());
    }

    /** 直接归档（不公示，0723 补实现）：终局动作，会议移入资料库并从日常列表隐藏 */
    @PostMapping("/{id}/archive")
    @RequireRole({"主任", "副主任"})
    public Result<Void> archive(@PathVariable Long id) {
        service.archive(id);
        return Result.ok();
    }

    /** 撤销归档（仅误归档用，原因必填；已公示需先撤回公示） */
    @PostMapping("/{id}/archive/revoke")
    @RequireRole({"主任", "副主任"})
    @RequirePermission(SystemPermission.FORMAL_ARCHIVE_REVOKE)
    public Result<Void> revokeArchive(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> req) {
        service.revokeArchive(id, req == null ? null : (String) req.get("reason"));
        return Result.ok();
    }

    @GetMapping("/members")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<List<MeetingDetailVO.MemberSummaryVO>> members() {
        return Result.ok(service.listCommitteeMembers());
    }

    @GetMapping("/{id}")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<MeetingDetailVO> detail(@PathVariable Long id) {
        return Result.ok(service.getDetail(id));
    }

    @PostMapping
    @RequireRole({"主任", "副主任"})
    public Result<CommitteeMeeting> create(@RequestBody CreateMeetingRequest req) {
        return Result.ok(service.createMeeting(req));
    }

    /**
     * 新建会议——上传文档/拍照件，OCR + 大模型判类（通知/材料）并抽取会议信息。
     * 文件先落库存储（返回 fileUrl），识别失败也能作为会议材料挂载；OCR/AI 未开启或失败时
     * 返回 available=false + message（前端提示后回退手动填写），不报错。
     */
    @PostMapping("/parse-document")
    @RequireRole({"主任", "副主任"})
    public Result<MeetingPrefillVO> parseDocument(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return Result.fail("文件为空");
        String name = file.getOriginalFilename();
        String ext = extractExt(name);
        byte[] data = file.getBytes();
        MeetingPrefillVO vo = prefillService.parse(name, ext, data);
        try {
            vo.setFileUrl(audioStorage.save(0L, data, ext.isBlank() ? "bin" : ext));
            vo.setFileName(name == null ? "" : name);
            vo.setFileType(ext);
            vo.setFileSize(file.getSize());
        } catch (Exception e) { /* 存储失败不影响识别结果返回 */ }
        return Result.ok(vo);
    }

    /**
     * 新建会议——一次上传多张照片/多个文件，统一 OCR + 大模型识别：逐个判类（通知/材料），
     * 通知类合并抽取会议信息预填，材料类回传 fileUrl 供挂载。返回 MeetingPrefillVO（含 files 列表）。
     */
    @PostMapping("/parse-documents")
    @RequireRole({"主任", "副主任"})
    public Result<MeetingPrefillVO> parseDocuments(@RequestParam("files") MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) return Result.fail("文件为空");
        java.util.List<DocumentPrefillService.Doc> docs = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            docs.add(new DocumentPrefillService.Doc(
                    f.getOriginalFilename(), extractExt(f.getOriginalFilename()), f.getSize(), f.getBytes()));
        }
        if (docs.isEmpty()) return Result.fail("文件为空");
        MeetingPrefillVO vo = prefillService.parseMulti(docs);
        // 逐个落库存储，回填 fileUrl（顺序与 docs 一致）；存储失败不影响识别结果返回。
        for (int i = 0; i < docs.size() && i < vo.getFiles().size(); i++) {
            DocumentPrefillService.Doc d = docs.get(i);
            MeetingPrefillVO.FileInfo fi = vo.getFiles().get(i);
            try {
                fi.setFileUrl(audioStorage.save(0L, d.data(), d.fileType().isBlank() ? "bin" : d.fileType()));
            } catch (Exception e) { /* 单个存储失败不影响其余 */ }
        }
        return Result.ok(vo);
    }

    private String extractExt(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    @DeleteMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> remove(@PathVariable Long id) {
        service.removeMeeting(id);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> update(@PathVariable Long id, @RequestBody CreateMeetingRequest req) {
        service.updateMeeting(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/notice-draft")
    @RequireRole({"主任", "副主任"})
    public Result<Void> updateNoticeDraft(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        service.updateNoticeDraft(id,
                req == null ? null : (String) req.get("title"),
                req == null ? null : (String) req.get("content"));
        return Result.ok();
    }

    @PostMapping("/{id}/advance")
    @RequireRole({"主任", "副主任"})
    public Result<Void> advance(@PathVariable Long id, @RequestParam String action,
                                @RequestParam(required = false) String mode) {
        service.advanceStage(id, action, mode);
        return Result.ok();
    }

    /** 主任手动修正会议有效性判定（自动判定有误时纠正：valid/flawed/invalid） */
    @PutMapping("/{id}/compliance")
    @RequireRole({"主任", "副主任"})
    public Result<Void> setCompliance(@PathVariable Long id, @RequestParam String status) {
        service.setCompliance(id, status);
        return Result.ok();
    }

    // 0803 收紧（codex 审查点6）：改他人送达位是主任的通知管理动作，委员不可调
    @PutMapping("/{id}/delivery/{userRoleId}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> toggleDelivery(@PathVariable Long id, @PathVariable Long userRoleId,
                                       @RequestParam String field) {
        service.toggleDelivery(id, userRoleId, field);
        return Result.ok();
    }

    @PostMapping("/{id}/delivery/read")
    public Result<Void> markDeliveryRead(@PathVariable Long id) {
        service.markDeliveryRead(id);
        return Result.ok();
    }

    // 0803 收紧（codex 审查点6）：整场通知名单只允许主任/副主任发起，委员不可覆盖
    @PostMapping("/{id}/delivery/send-all")
    @RequireRole({"主任", "副主任"})
    public Result<Void> sendAll(@PathVariable Long id,
                                @RequestBody(required = false) DeliverySendRequest req) {
        service.sendAll(id, req == null ? null : req.getMemberIds());
        return Result.ok();
    }

    @PostMapping("/{id}/delivery/wechat-mark")
    @RequireRole({"主任", "副主任"})
    public Result<Void> markWechatNotified(@PathVariable Long id) {
        service.markWechatNotified(id);
        return Result.ok();
    }

    // 清空通知记录：删通知历史+送达记录、重置为未通知（0728 用户定为正式功能，见 service 注释）。
    @PostMapping("/{id}/delivery/clear")
    @RequireRole({"主任", "副主任"})
    public Result<Void> clearNotifications(@PathVariable Long id) {
        service.clearNotifications(id);
        return Result.ok();
    }

    // 0803 收紧（codex 审查点6）：改他人签到只允许主任/副主任；委员只能走 /self 本人签到
    @PutMapping("/{id}/attendance/{userRoleId}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> toggleAttendance(@PathVariable Long id, @PathVariable Long userRoleId,
                                          @RequestParam String field) {
        service.toggleAttendance(id, userRoleId, field);
        return Result.ok();
    }

    /** 主持人修正参会状态（名单弹窗下拉）：onsite/remote/declined/none */
    @PutMapping("/{id}/attendance/{userRoleId}/status")
    @RequireRole({"主任", "副主任"})
    public Result<Void> setAttendanceStatus(@PathVariable Long id, @PathVariable Long userRoleId,
                                            @RequestParam String value) {
        service.setAttendanceStatus(id, userRoleId, value);
        return Result.ok();
    }

    @PutMapping("/{id}/self")
    public Result<Void> selfToggle(@PathVariable Long id, @RequestParam String field) {
        service.selfToggle(id, field);
        return Result.ok();
    }

    @PutMapping("/{id}/self/attendance")
    public Result<Void> selfAttend(@PathVariable Long id,
                                   @RequestParam String mode,
                                   @RequestParam(required = false, defaultValue = "false") Boolean authorizeProxySign) {
        service.selfAttend(id, mode, Boolean.TRUE.equals(authorizeProxySign));
        return Result.ok();
    }

    @GetMapping("/{id}/attendance/export")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Map<String, Object>> exportAttendance(@PathVariable Long id) {
        return Result.ok(service.exportAttendanceCsv(id));
    }

    @GetMapping("/{id}/attendance-sheet.pdf")
    @RequireRole({"主任", "副主任", "记录员"})
    public ResponseEntity<byte[]> exportAttendanceSheet(@PathVariable Long id) {
        AttendanceSheetPdfService.PdfFile file = attendanceSheetPdfService.generate(id);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(file.bytes());
    }

    /** 会议纪要 PDF：正文取自已生成/已编辑的纪要（generateMinutes 同源），公文格式落页 */
    @GetMapping("/{id}/minutes.pdf")
    @RequireRole({"主任", "副主任", "委员"})
    public ResponseEntity<byte[]> exportMinutesPdf(@PathVariable Long id) {
        MeetingRecordPdfService.PdfFile file = meetingRecordPdfService.generateMinutesPdf(id, service.generateMinutes(id));
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(file.bytes());
    }

    /** 列席人员（居委/街道/物业等非委员到会者）：会后整理页登记，进入会议记录与纪要 */
    @PutMapping("/{id}/observers")
    @RequireRole({"主任", "副主任"})
    public Result<Void> setObservers(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        service.setObservers(id, req == null ? null : (String) req.get("text"));
        return Result.ok();
    }

    /** 会前公告文本（预览用）：向全体业主公告会议议程、征求意见（会前7天） */
    @GetMapping("/{id}/pre-notice")
    @RequireRole({"主任", "副主任"})
    public Result<String> preNotice(@PathVariable Long id) {
        return Result.ok(service.buildPreNoticeContent(id));
    }

    /** 会前公告 PDF：主任在准备阶段导出，打印张贴公示栏 */
    @GetMapping("/{id}/pre-notice.pdf")
    @RequireRole({"主任", "副主任"})
    public ResponseEntity<byte[]> exportPreNotice(@PathVariable Long id) {
        MeetingRecordPdfService.PdfFile file = meetingRecordPdfService.generateNoticePdf(id, service.buildPreNoticeContent(id));
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(file.bytes());
    }

    /** 会后公示 PDF（0723）：公示页一键导出打印，张贴到小区公告栏。已公示导存档正文，未公示导当前预览。 */
    @GetMapping("/{id}/public-notice.pdf")
    @RequireRole({"主任", "副主任"})
    public ResponseEntity<byte[]> exportPublicNotice(@PathVariable Long id) {
        MeetingRecordPdfService.PdfFile file = meetingRecordPdfService.generateNoticePdf(id, service.publicNoticeText(id), "会议公示");
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(file.bytes());
    }

    /** 归档材料目录（编号台账）：会议记录/纪要/公示 + 会议材料 + 补充材料。先做后台端点，查看入口后续接 */
    @GetMapping("/{id}/archive-catalog")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Map<String, Object>> archiveCatalog(@PathVariable Long id) {
        return Result.ok(service.buildArchiveCatalog(id));
    }

    /** 会议记录纯文本：页内预览用，与 meeting-record.pdf 同一份内容装配（所见即所导） */
    @GetMapping("/{id}/meeting-record-text")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<String> meetingRecordText(@PathVariable Long id) {
        return Result.ok(meetingRecordPdfService.generateRecordText(id));
    }

    @GetMapping("/{id}/meeting-record.pdf")
    @RequireRole({"主任", "副主任", "委员"})
    public ResponseEntity<byte[]> exportMeetingRecord(@PathVariable Long id) {
        MeetingRecordPdfService.PdfFile file = meetingRecordPdfService.generate(id);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(file.bytes());
    }

    @PostMapping("/{id}/attendance/sign-all")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> signAll(@PathVariable Long id) {
        service.signAll(id);
        return Result.ok();
    }

    /** 线上会议由主任/副主任一次性登记实际参会名单，不触发个人签到流程。 */
    @PutMapping("/{id}/online-attendance")
    @RequireRole({"主任", "副主任"})
    public Result<Void> setOnlineAttendance(@PathVariable Long id,
                                            @RequestBody OnlineAttendanceRequest req) {
        service.setOnlineAttendance(id, req == null ? List.of() : req.getPresentMemberIds());
        return Result.ok();
    }

    @PostMapping("/{id}/topics")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<java.util.Map<String, Object>> addTopic(@PathVariable Long id,
                                         @RequestParam String title,
                                         @RequestParam String type,
                                         @RequestParam(required = false) String decisionType,
                                         @RequestParam(required = false) String options,
                                         @RequestParam(required = false, defaultValue = "false") Boolean realNameVote,
                                         @RequestParam(required = false) String content) {
        // 仅返回必要字段，避免直接序列化 JPA 实体触发 Hibernate 懒加载代理(community 等)序列化失败
        RecordTopic t = service.addTopic(id, title, type, decisionType, options, realNameVote, content);
        java.util.Map<String, Object> vo = new java.util.HashMap<>();
        vo.put("id", t.getId());
        vo.put("title", t.getTitle());
        return Result.ok(vo);
    }

    @DeleteMapping("/{id}/topics/{topicId}")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> removeTopic(@PathVariable Long id, @PathVariable Long topicId) {
        service.removeTopic(id, topicId);
        return Result.ok();
    }

    /** 通报议题：记录当前用户已查看（会议参会名单全体看完即自动已通报）。 */
    @PostMapping("/{id}/topics/{topicId}/notice-view")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> noticeView(@PathVariable Long id, @PathVariable Long topicId) {
        service.markNoticeViewed(id, topicId);
        return Result.ok();
    }

    /** 通报议题：有人「已宣读」→ 标记已通报。 */
    @PostMapping("/{id}/topics/{topicId}/notice-read")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> noticeRead(@PathVariable Long id, @PathVariable Long topicId) {
        service.markNoticeRead(id, topicId);
        return Result.ok();
    }

    @PutMapping("/{id}/topics/{topicId}/title")
    @RequireRole({"主任", "副主任"})
    public Result<Void> renameTopic(@PathVariable Long id, @PathVariable Long topicId,
                                    @RequestParam String title) {
        service.renameTopic(id, topicId, title);
        return Result.ok();
    }

    @PutMapping("/{id}/topics/{topicId}/vote")
    public Result<Void> vote(@PathVariable Long id, @PathVariable Long topicId,
                              @RequestParam(required = false) String choice,
                              @RequestParam(required = false) Long selectedId) {
        service.vote(id, topicId, choice, selectedId);
        return Result.ok();
    }

    /** 撤回本人投票（表决未结束前）：回到未投状态，可重新投票。 */
    @DeleteMapping("/{id}/topics/{topicId}/vote")
    public Result<Void> retractVote(@PathVariable Long id, @PathVariable Long topicId) {
        service.retractVote(id, topicId);
        return Result.ok();
    }

    /** 结束表决（主任/副主任）：揭晓票数并公布结果。 */
    @PostMapping("/{id}/topics/{topicId}/close-vote")
    @RequireRole({"主任", "副主任"})
    public Result<Void> closeVote(@PathVariable Long id, @PathVariable Long topicId) {
        service.closeVote(id, topicId);
        return Result.ok();
    }

    // ===== 议题意见 =====

    @GetMapping("/{id}/opinions")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<List<Map<String, Object>>> listOpinions(@PathVariable Long id) {
        return Result.ok(service.listOpinions(id));
    }

    @PostMapping("/{id}/topics/{topicId}/opinions")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Map<String, Object>> addOpinion(@PathVariable Long id, @PathVariable Long topicId,
                                                  @RequestBody Map<String, Object> req) {
        String content = req == null ? null : (String) req.get("content");
        String source = req == null ? null : (String) req.get("source");
        return Result.ok(service.addOpinion(id, topicId, content, source));
    }

    @PutMapping("/{id}/opinions/{opinionId}")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> updateOpinion(@PathVariable Long id, @PathVariable Long opinionId,
                                      @RequestBody Map<String, Object> req) {
        service.updateOpinion(id, opinionId, req == null ? null : (String) req.get("content"));
        return Result.ok();
    }

    @DeleteMapping("/{id}/opinions/{opinionId}")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> removeOpinion(@PathVariable Long id, @PathVariable Long opinionId) {
        service.removeOpinion(id, opinionId);
        return Result.ok();
    }

    /** 认领/指派 AI 提炼的现场意见：不带 userRoleId 是本人认领；带 userRoleId 仅主任/副主任可指派。 */
    @PutMapping("/{id}/opinions/{opinionId}/claim")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Map<String, Object>> claimOpinion(@PathVariable Long id, @PathVariable Long opinionId,
                                                    @RequestBody(required = false) Map<String, Object> req) {
        Object v = req == null ? null : req.get("userRoleId");
        Long assign = v == null ? null : Long.valueOf(String.valueOf(v));
        return Result.ok(service.claimOpinion(id, opinionId, assign));
    }

    /** 意见 AI 助手：mode=polish 润色已有意见 / mode=draft 按口头描述代拟发言。只回文本不入库。 */
    @PostMapping("/{id}/topics/{topicId}/opinions/assist")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Map<String, Object>> assistOpinion(@PathVariable Long id, @PathVariable Long topicId,
                                                     @RequestBody Map<String, Object> req) {
        String mode = req == null ? null : (String) req.get("mode");
        String text = req == null ? null : (String) req.get("text");
        return Result.ok(service.assistOpinion(id, topicId, mode, text));
    }

    /** 意见语音输入：浏览器录音（webm/mp4）直传，同步转文字返回，前端填入可编辑输入框。 */
    @PostMapping("/{id}/asr")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Map<String, Object>> voiceToText(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return Result.fail("音频为空");
        String text = service.recognizeVoice(id, file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return Result.ok(Map.of("text", text == null ? "" : text));
    }

    @GetMapping("/{id}/proxy-targets")
    @RequireRole({"主任", "副主任"})
    public Result<List<ProxyTargetVO>> proxyTargets(@PathVariable Long id,
                                                    @RequestParam(required = false) String keyword) {
        return Result.ok(service.listProxyTargets(id, keyword));
    }

    @PostMapping("/{id}/proxy-actions")
    @RequireRole({"主任", "副主任"})
    public Result<Void> proxyAction(@PathVariable Long id,
                                    @RequestBody ProxyActionRequest req) {
        service.proxyAction(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/flags")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> toggleFlag(@PathVariable Long id, @RequestParam String flag) {
        service.toggleFlag(id, flag);
        return Result.ok();
    }

    @PostMapping("/{id}/juwei")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> toggleJuwei(@PathVariable Long id) {
        service.toggleJuwei(id);
        return Result.ok();
    }

    @PostMapping("/{id}/evidences")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> addEvidence(@PathVariable Long id,
                                     @RequestParam String fileName,
                                     @RequestParam String fileType,
                                     @RequestParam(required = false) String fileUrl) {
        service.addEvidence(id, fileName, fileType, fileUrl);
        return Result.ok();
    }

    @DeleteMapping("/{id}/evidences/{evidenceId}")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> removeEvidence(@PathVariable Long id, @PathVariable Long evidenceId) {
        service.removeEvidence(id, evidenceId);
        return Result.ok();
    }

    // ===== 会议材料 =====
    @PostMapping("/{id}/materials")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Void> addMaterial(@PathVariable Long id,
                                    @RequestParam String fileName,
                                    @RequestParam(required = false) String sizeText,
                                    @RequestParam(required = false) String fileType,
                                    @RequestParam(required = false) String fileUrl) {
        service.addMaterial(id, fileName, fileType, sizeText, fileUrl);
        return Result.ok();
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Void> removeMaterial(@PathVariable Long id, @PathVariable Long materialId) {
        service.removeMaterial(id, materialId);
        return Result.ok();
    }

    // ===== 补充归档 =====
    @PostMapping("/{id}/archive-extras")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Void> addArchiveExtra(@PathVariable Long id,
                                        @RequestParam String fileName,
                                        @RequestParam(required = false) String sizeText,
                                        @RequestParam(required = false) String fileType,
                                        @RequestParam(required = false) String reason,
                                        @RequestParam(required = false) String fileUrl) {
        String addedBy = com.ywh.util.SecurityUtils.getCurrentRealName();
        service.addArchiveExtra(id, fileName, fileType, sizeText, reason, fileUrl, addedBy);
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return Result.ok();
    }

    @PostMapping("/{id}/publish/withdraw")
    @RequireRole({"主任", "副主任"})
    @RequirePermission(SystemPermission.FORMAL_ARCHIVE_REVOKE)
    public Result<Void> withdrawPublish(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        service.withdrawPublish(id, req == null ? null : (String) req.get("reason"));
        return Result.ok();
    }

    @GetMapping("/{id}/minutes")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<String> minutes(@PathVariable Long id) {
        return Result.ok(service.generateMinutes(id));
    }

    /**
     * AI 生成党建新闻：改为「发起即返回」——后台 @Async 线程拿纪要喂大模型跑到底并落库，
     * 请求线程不再阻塞等大模型。前端拿到 running 后轮询 /news-status；退微信/锁屏都不影响后台跑完。
     * 30s 内已有 running 任务则不重复起（去重），直接回当前状态。
     */
    @PostMapping("/{id}/news")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<NewsTaskStatusVO> generateNews(@PathVariable Long id) {
        if (newsTaskService.markRunningIfNeeded(id)) newsAsyncWorker.generate(id);
        return Result.ok(newsTaskService.getStatus(id));
    }

    /** 党建新闻生成状态 + 结果：none/running/success/failed；success 直接带回标题+正文，供切回页面查看。 */
    @GetMapping("/{id}/news-status")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<NewsTaskStatusVO> newsStatus(@PathVariable Long id) {
        return Result.ok(newsTaskService.getStatus(id));
    }

    @GetMapping("/{id}/minutes/revisions")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<List<MeetingDetailVO.MinutesRevisionVO>> minutesRevisions(@PathVariable Long id) {
        return Result.ok(service.listMinutesRevisions(id));
    }

    @PutMapping("/{id}/minutes")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Void> updateMinutes(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        service.updateMinutes(id, req == null ? null : (String) req.get("text"));
        return Result.ok();
    }

    @GetMapping("/stats")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<Map<String, Object>> stats() {
        return Result.ok(service.getStats());
    }

    @GetMapping("/publish-score")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<MeetingDetailVO.PublishScoreVO> publishScore() {
        return Result.ok(service.getPublishScore());
    }
}
