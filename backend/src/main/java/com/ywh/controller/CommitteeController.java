package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.dto.CreateMeetingRequest;
import com.ywh.dto.DeliverySendRequest;
import com.ywh.dto.MeetingDetailVO;
import com.ywh.dto.ProxyActionRequest;
import com.ywh.dto.ProxyTargetVO;
import com.ywh.entity.CommitteeMeeting;
import com.ywh.entity.RecordTopic;
import com.ywh.service.CommitteeService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/committees")
@RequiredArgsConstructor
public class CommitteeController {

    private final CommitteeService service;

    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String stage) {
        return Result.ok(service.listMeetings(stage));
    }

    @GetMapping("/members")
    public Result<List<MeetingDetailVO.MemberSummaryVO>> members() {
        return Result.ok(service.listCommitteeMembers());
    }

    @GetMapping("/{id}")
    public Result<MeetingDetailVO> detail(@PathVariable Long id) {
        return Result.ok(service.getDetail(id));
    }

    @PostMapping
    @RequireRole({"主任", "副主任"})
    public Result<CommitteeMeeting> create(@RequestBody CreateMeetingRequest req) {
        return Result.ok(service.createMeeting(req));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> remove(@PathVariable Long id) {
        service.removeMeeting(id);
        return Result.ok();
    }

    @PostMapping("/{id}/advance")
    @RequireRole({"主任", "副主任"})
    public Result<Void> advance(@PathVariable Long id, @RequestParam String action,
                                @RequestParam(required = false) String mode) {
        service.advanceStage(id, action, mode);
        return Result.ok();
    }

    @PutMapping("/{id}/delivery/{userRoleId}")
    @RequireRole({"主任", "副主任", "委员"})
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

    @PostMapping("/{id}/delivery/send-all")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> sendAll(@PathVariable Long id,
                                @RequestBody(required = false) DeliverySendRequest req) {
        service.sendAll(id, req == null ? null : req.getMemberIds());
        return Result.ok();
    }

    @PutMapping("/{id}/attendance/{userRoleId}")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> toggleAttendance(@PathVariable Long id, @PathVariable Long userRoleId,
                                          @RequestParam String field) {
        service.toggleAttendance(id, userRoleId, field);
        return Result.ok();
    }

    @PutMapping("/{id}/self")
    public Result<Void> selfToggle(@PathVariable Long id, @RequestParam String field) {
        service.selfToggle(id, field);
        return Result.ok();
    }

    @GetMapping("/{id}/attendance/export")
    @RequireRole({"主任", "副主任", "记录员"})
    public Result<Map<String, Object>> exportAttendance(@PathVariable Long id) {
        return Result.ok(service.exportAttendanceCsv(id));
    }

    @PostMapping("/{id}/attendance/sign-all")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> signAll(@PathVariable Long id) {
        service.signAll(id);
        return Result.ok();
    }

    @PostMapping("/{id}/topics")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<RecordTopic> addTopic(@PathVariable Long id,
                                         @RequestParam String title,
                                         @RequestParam String type,
                                         @RequestParam(required = false) String decisionType,
                                         @RequestParam(required = false) String options,
                                         @RequestParam(required = false, defaultValue = "false") Boolean realNameVote) {
        return Result.ok(service.addTopic(id, title, type, decisionType, options, realNameVote));
    }

    @DeleteMapping("/{id}/topics/{topicId}")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> removeTopic(@PathVariable Long id, @PathVariable Long topicId) {
        service.removeTopic(id, topicId);
        return Result.ok();
    }

    @PutMapping("/{id}/topics/{topicId}/vote")
    public Result<Void> vote(@PathVariable Long id, @PathVariable Long topicId,
                              @RequestParam(required = false) String choice,
                              @RequestParam(required = false) Long selectedId) {
        service.vote(id, topicId, choice, selectedId);
        return Result.ok();
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
                                     @RequestParam String fileType) {
        service.addEvidence(id, fileName, fileType);
        return Result.ok();
    }

    @DeleteMapping("/{id}/evidences/{evidenceId}")
    @RequireRole({"主任", "副主任", "委员"})
    public Result<Void> removeEvidence(@PathVariable Long id, @PathVariable Long evidenceId) {
        service.removeEvidence(id, evidenceId);
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
    public Result<Void> withdrawPublish(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        service.withdrawPublish(id, req == null ? null : (String) req.get("reason"));
        return Result.ok();
    }

    @GetMapping("/{id}/minutes")
    public Result<String> minutes(@PathVariable Long id) {
        return Result.ok(service.generateMinutes(id));
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
    public Result<Map<String, Object>> stats() {
        return Result.ok(service.getStats());
    }

    @GetMapping("/publish-score")
    public Result<MeetingDetailVO.PublishScoreVO> publishScore() {
        return Result.ok(service.getPublishScore());
    }
}
