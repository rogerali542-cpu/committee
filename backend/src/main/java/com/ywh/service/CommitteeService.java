package com.ywh.service;

import com.ywh.dto.CreateMeetingRequest;
import com.ywh.dto.MeetingDetailVO;
import com.ywh.dto.MeetingDetailVO.*;
import com.ywh.dto.MeetingTodoVO;
import com.ywh.dto.ProxyActionRequest;
import com.ywh.dto.ProxyTargetVO;
import com.ywh.dto.RecordingVO;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickPolishVO;
import com.ywh.dto.quick.QuickConfirmRequest;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.MinutesTaskStatusVO;
import com.ywh.entity.*;
import com.ywh.enums.*;
import com.ywh.repository.*;
import com.ywh.util.SecurityUtils;
import com.ywh.service.quick.DoubaoOcrService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommitteeService {

    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingDeliveryRepository deliveryRepo;
    private final MeetingRecordRepository recordRepo;
    private final RecordAttendanceRepository attendanceRepo;
    private final RecordTopicRepository topicRepo;
    private final TopicVoteRepository voteRepo;
    private final RecordEvidenceRepository evidenceRepo;
    private final MeetingPublishRepository publishRepo;
    private final MinutesRevisionRepository minutesRevisionRepo;
    private final MeetingRecordingRepository recordingRepo;
    private final UserRoleRepository userRoleRepo;
    private final MeetingMaterialRepository materialRepo;
    private final ArchiveExtraRepository archiveExtraRepo;
    private final MinutesTaskRepository minutesTaskRepo;
    private final ObjectMapper objectMapper;
    // 可选：仅 doubao.ocr.enabled=true 时存在，未启用时 getIfAvailable() 返回 null → OCR 跳过
    private final ObjectProvider<DoubaoOcrService> ocrServiceProvider;
    private final MeetingTodoRepository todoRepo;
    private final MeetingNotificationLogRepository notificationLogRepo;
    private final TopicOpinionRepository opinionRepo;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    // ===== List =====
    public List<Map<String, Object>> listMeetings(String stage) {
        return listMeetings(stage, null);
    }

    /** archived=true → 只看已归档（资料库）；默认排除已归档（日常列表不再出现，这正是归档的意义）。 */
    public List<Map<String, Object>> listMeetings(String stage, Boolean archived) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        UserRoleEntity currentUr = SecurityUtils.getCurrentUserRole();
        // 物业不参与小区行政，不开放业委会会议
        if (currentUr.getRole().isPropertyMgmt()) {
            return Collections.emptyList();
        }

        List<CommitteeMeeting> meetings;
        if (isExternal(currentUr)) {
            // External: meetings ever published — currently public ones show content,
            // withdrawn/voided ones show a tombstone (内容不可见但保留状态)。见 §5
            meetings = meetingRepo.findByCommunityIdAndStageOrderByCreatedAtDesc(
                    communityId, MeetingStage.valueOf(stage));
            meetings = meetings.stream()
                    .filter(m -> externalCanSeeMeeting(currentUr, m))
                    .collect(Collectors.toList());
        } else if (isChair(currentUr) || isRecorder(currentUr)) {
            // Chair/recorder: see all
            if (stage != null) {
                meetings = meetingRepo.findByCommunityIdAndStageOrderByCreatedAtDesc(
                        communityId, MeetingStage.valueOf(stage));
            } else {
                meetings = meetingRepo.findByCommunityIdOrderByCreatedAtDesc(communityId);
            }
        } else {
            // Committee member: see relevant only
            if (stage != null) {
                meetings = meetingRepo.findByCommunityIdAndStageOrderByCreatedAtDesc(
                        communityId, MeetingStage.valueOf(stage));
            } else {
                meetings = meetingRepo.findByCommunityIdOrderByCreatedAtDesc(communityId);
            }
            meetings = meetings.stream()
                    .filter(m -> isRelevantToMember(m, currentUr))
                    .collect(Collectors.toList());
        }

        boolean archivedOnly = Boolean.TRUE.equals(archived);
        meetings = meetings.stream()
                .filter(m -> archivedOnly == Boolean.TRUE.equals(m.getArchived()))
                .collect(Collectors.toList());

        return meetings.stream().map(m -> {
            Map<String, Object> card = new HashMap<>();
            card.put("id", m.getId());
            card.put("_archived", Boolean.TRUE.equals(m.getArchived()));
            card.put("title", m.getTitle());
            card.put("meetingDate", m.getMeetingDate());
            card.put("meetingTime", m.getMeetingTime());
            card.put("location", m.getLocation());
            card.put("stage", m.getStage().name());
            card.put("compliance", m.getCompliance() != null ? m.getCompliance().name() : null);
            card.put("summaryLine", getRoleSummaryLine(m, currentUr));
            card.put("roleView", getRoleView(currentUr));
            // 简洁模式「我要办理」按 progress 筛选；「资料库」按 publish 显示徽标
            Map<String, Object> prog = getCardProgress(m, currentUr);
            card.put("progress", prog.get("pct"));
            card.put("progressLabel", prog.get("label"));
            card.put("publish", m.getStage() == MeetingStage.ended
                    && m.getCompliance() != ComplianceStatus.invalid ? getPublishInfo(m) : null);
            // 待办用：准备阶段、通知已送达我但我尚未查看 → 生成"查看会议通知"待办
            card.put("myNoticeUnread", isNoticeUnreadForMe(m, currentUr));
            // 准备阶段：被通知委员是否都已回复（确认/缺席）→ 首页主按钮显示"会议已就绪"
            card.put("allReplied", isPreparingAllReplied(m));
            // ended 会议：纪要是否已生成 → 前端把"整理会议记录"改为"查看会议"
            card.put("minutesGenerated", m.getStage() == MeetingStage.ended
                    && recordRepo.findByMeetingId(m.getId())
                        .map(rec -> rec.getMinutesText() != null && !rec.getMinutesText().isBlank())
                        .orElse(false));
            return card;
        }).collect(Collectors.toList());
    }

    /** 准备阶段 + 通知已送达当前用户但其尚未查看 → true。供待办聚合。 */
    private boolean isNoticeUnreadForMe(CommitteeMeeting m, UserRoleEntity ur) {
        if (m.getStage() != MeetingStage.preparing || ur == null) return false;
        return deliveryRepo.findByMeetingIdAndUserRoleId(m.getId(), ur.getId())
                .map(d -> Boolean.TRUE.equals(d.getNoticeDelivered()) && d.getNoticeReadAt() == null)
                .orElse(false);
    }

    public List<MemberSummaryVO> listCommitteeMembers() {
        return findCommitteeMembers(SecurityUtils.getCurrentCommunityId()).stream()
                .map(this::toMemberSummary)
                .collect(Collectors.toList());
    }

    // ===== Detail =====
    @Transactional
    public MeetingDetailVO getDetail(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur.getRole().isPropertyMgmt()) {
            throw new IllegalArgumentException("物业不参与小区行政事务，无权查看业委会会议");
        }
        String roleView = getRoleView(ur);
        Map<String, Object> taskSummary = getTaskSummary(m, ur);
        boolean hideInternalRecord = isExternal(ur) && !canSeePublished(ur, m);

        // 公示状态不分阶段一律下发（0730 修）：测试期会议长期 ongoing，若只在 ended 才给，
        // 发布成功后前端刷新拿到 publish=null，会把「确认发布公示」按钮又放出来、可反复发布
        PublishInfoVO publishInfo = getPublishInfo(m);
        if (publishInfo != null && isExternal(ur)) {
            redactPublishForExternal(publishInfo);
        }

        return MeetingDetailVO.builder()
                .id(m.getId())
                .title(m.getTitle())
                .meetingDate(m.getMeetingDate())
                .meetingTime(m.getMeetingTime())
                .location(m.getLocation())
                .locationLat(m.getLocationLat())
                .locationLng(m.getLocationLng())
                .meetingMethod(m.getMeetingMethod())
                .description(m.getDescription())
                .stage(m.getStage())
                .compliance(m.getCompliance())
                .meetingMode(m.getMeetingMode())
                .userRole(ur.getRole().name())
                .userView(roleView)
                .coreLocked(m.getStage() != MeetingStage.preparing || m.getNotifiedAt() != null)
                .notifiedAt(m.getNotifiedAt() != null ? m.getNotifiedAt().toString() : null)
                .notifiedByName(m.getNotifiedByName())
                .notificationLogs(buildNotificationLogs(m.getId()))
                .taskLevel((String) taskSummary.get("level"))
                .taskTitle((String) taskSummary.get("title"))
                .taskItems((List<String>) taskSummary.get("items"))
                .taskHint((String) taskSummary.get("hint"))
                .flowNodeText(getFlowNodeText(m))
                .delivery(hideInternalRecord ? null : getDeliveryInfo(m))
                .noticeDraft(buildNoticeDraft(m))
                .materials(buildMaterials(m.getId()))
                .archiveExtras(buildArchiveExtras(m.getId()))
                .myDelivery(hideInternalRecord ? null : getMyDelivery(m, ur))
                .record(hideInternalRecord ? null : getRecordInfo(m))
                .minutesReady(!hideInternalRecord && recordRepo.findByMeetingId(meetingId)
                        .map(rec -> rec.getMinutesText() != null && !rec.getMinutesText().isBlank())
                        .orElse(false))
                .publish(publishInfo)
                .archivedFlag(Boolean.TRUE.equals(m.getArchived()))
                .members(hideInternalRecord ? null : getMemberSummaries(m))
                .orgFullName(orgFullName(m))
                .observers(hideInternalRecord ? null : recordRepo.findByMeetingId(meetingId)
                        .map(MeetingRecord::getObserversText).orElse(null))
                .build();
    }

    /** 列席人员（居委/街道/物业等非委员到会者）：主任在会后整理页登记，进入记录与纪要。 */
    @Transactional
    public void setObservers(Long meetingId, String text) {
        meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        MeetingRecord record = getRecord(meetingId);
        record.setObserversText(text == null ? null : text.trim());
        recordRepo.save(record);
    }

    // ===== Create =====
    @Transactional
    public CommitteeMeeting createMeeting(CreateMeetingRequest req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        Long userId = SecurityUtils.getCurrentUserId();
        List<CreateMeetingRequest.TopicRequest> meetingTopics = normalizeCreateTopics(req.getTopics());
        CommitteeMeeting m = CommitteeMeeting.builder()
                .community(Community.builder().id(communityId).build())
                .title(req.getTitle())
                .meetingDate(req.getMeetingDate())
                .meetingTime(req.getMeetingTime() != null ? req.getMeetingTime() : java.time.LocalTime.of(10, 0))
                .location(req.getLocation() != null ? req.getLocation() : "待定")
                .locationLat(req.getLocationLat())
                .locationLng(req.getLocationLng())
                .meetingMethod(req.getMeetingMethod() != null ? req.getMeetingMethod() : com.ywh.enums.MeetingMethod.offline)
                .stage(MeetingStage.preparing)
                .description(req.getDescription())
                .createdBy(userId)
                .build();
        m = meetingRepo.save(m);

        MeetingRecord record = MeetingRecord.builder()
                .meeting(m)
                .hasDecision(true)
                .hasMajorIssue(meetingTopics.stream().anyMatch(t -> "major".equals(t.getType())))
                .juweiName("王红梅（社区居委会）")
                .juweiSigned(false)
                .build();
        record = recordRepo.save(record);
        savePresetTopics(record, meetingTopics);

        // 通知草稿放在议题入库之后再生成，正文才能带上"会议议题"逐条；
        // （原来在建 record 之前生成 → buildNoticeTopicsText 取不到议题，恒为"待补充"。updateMeeting 一直是这个顺序）
        applyGeneratedNoticeDraft(m);
        m = meetingRepo.save(m);

        return m;
    }

    @Transactional
    public void updateMeeting(Long meetingId, CreateMeetingRequest req) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.preparing) {
            throw new IllegalArgumentException("仅准备阶段的会议可以编辑");
        }
        if (req.getTitle() != null) m.setTitle(req.getTitle());
        if (req.getMeetingDate() != null) m.setMeetingDate(req.getMeetingDate());
        if (req.getMeetingTime() != null) m.setMeetingTime(req.getMeetingTime());
        if (req.getLocation() != null) m.setLocation(req.getLocation());
        // 0801 修：编辑会议时地图选点的新坐标原先不落库（只更新地点文字），详情页导航仍指旧位置。
        // updateLocationCoords=true 的调用方（发起/编辑会议表单、线上线下转换）按原样写入，
        // 允许置 null——从地图选点改回手填/常用地点时必须能清掉旧坐标；
        // 其余调用方维持"非空才覆盖"，不传坐标就不动。
        if (Boolean.TRUE.equals(req.getUpdateLocationCoords())) {
            m.setLocationLat(req.getLocationLat());
            m.setLocationLng(req.getLocationLng());
        } else {
            if (req.getLocationLat() != null) m.setLocationLat(req.getLocationLat());
            if (req.getLocationLng() != null) m.setLocationLng(req.getLocationLng());
        }
        if (req.getMeetingMethod() != null) {
            m.setMeetingMethod(req.getMeetingMethod());
            if (req.getMeetingMethod() == com.ywh.enums.MeetingMethod.online
                    && (req.getLocation() == null || req.getLocation().isBlank())) m.setLocation("微信工作群");
        }
        if (req.getDescription() != null) m.setDescription(req.getDescription());
        // 带来了议题列表（来自"发起业委会"编辑表单）→ 重建预设议题；
        // 不带 topics 的调用方（通知草稿编辑弹窗只传标题/时间/地点/正文）不进此分支，议题保持不变。
        if (req.getTopics() != null && !req.getTopics().isEmpty()) {
            List<CreateMeetingRequest.TopicRequest> meetingTopics = normalizeCreateTopics(req.getTopics());
            MeetingRecord record = recordRepo.findByMeetingId(meetingId).orElse(null);
            if (record == null) {
                record = recordRepo.save(MeetingRecord.builder()
                        .meeting(m).hasDecision(true)
                        .hasMajorIssue(meetingTopics.stream().anyMatch(t -> "major".equals(t.getType())))
                        .juweiName("王红梅（社区居委会）").juweiSigned(false)
                        .build());
            } else {
                // 准备阶段无表决/意见数据，但仍按删会同款顺序清理依赖，防外键约束
                List<RecordTopic> old = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
                opinionRepo.deleteAll(opinionRepo.findByTopicRecordIdOrderByCreatedAtAsc(record.getId()));
                old.forEach(t -> voteRepo.deleteAll(voteRepo.findByTopicId(t.getId())));
                topicRepo.deleteAll(old);
                record.setHasMajorIssue(meetingTopics.stream().anyMatch(t -> "major".equals(t.getType())));
                recordRepo.save(record);
            }
            savePresetTopics(record, meetingTopics);
        }
        applyGeneratedNoticeDraft(m);
        meetingRepo.save(m);
    }

    @Transactional
    public void updateNoticeDraft(Long meetingId, String title, String content) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.preparing) {
            throw new IllegalArgumentException("仅准备阶段可以编辑通知草稿");
        }
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("通知标题和正文不能为空");
        }
        m.setNoticeTitle(title);
        m.setNoticeContent(content);
        m.setNoticeStatus("edited");
        meetingRepo.save(m);
    }

    // ===== Minutes generation task（纪要生成任务：落库成服务端可查状态，跨刷新/换设备/隔天可接上）=====
    /** 生成开始：复用 30s 内仍 running 的任务，否则新建 running；返回 taskId。 */
    @Transactional
    public Long markMinutesTaskRunning(Long meetingId) {
        MinutesTask t = minutesTaskRepo.findTopByMeetingIdOrderByIdDesc(meetingId).orElse(null);
        if (t != null && "running".equals(t.getStatus()) && t.getUpdatedAt() != null
                && t.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
            return t.getId();
        }
        MinutesTask nt = MinutesTask.builder().meetingId(meetingId).status("running").build();
        minutesTaskRepo.save(nt);
        return nt.getId();
    }

    /** 生成结束：把最新任务标记 success/failed。 */
    @Transactional
    public void markMinutesTaskDone(Long meetingId, boolean success, String err) {
        minutesTaskRepo.findTopByMeetingIdOrderByIdDesc(meetingId).ifPresent(t -> {
            t.setStatus(success ? "success" : "failed");
            if (err != null) t.setErrorMsg(err.length() > 500 ? err.substring(0, 500) : err);
            minutesTaskRepo.save(t);
        });
    }

    /** 最新纪要生成任务状态（none/running/success/failed）；running 超 6 分钟没更新视为 failed（卡死兜底）。 */
    @Transactional(readOnly = true)
    public MinutesTaskStatusVO getMinutesTaskStatus(Long meetingId) {
        MinutesTask t = minutesTaskRepo.findTopByMeetingIdOrderByIdDesc(meetingId).orElse(null);
        if (t == null) return MinutesTaskStatusVO.builder().status("none").build();
        String status = t.getStatus();
        if ("running".equals(status) && t.getUpdatedAt() != null
                && t.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(6))) {
            status = "failed";
        }
        return MinutesTaskStatusVO.builder().status(status).taskId(t.getId()).build();
    }

    // ===== Advance Stage =====
    @Transactional
    public void advanceStage(Long meetingId, String action, String mode) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));

        if ("start".equals(action)) {
            if (m.getStage() != MeetingStage.preparing) {
                throw new IllegalArgumentException("仅准备阶段的会议可以开始");
            }
            if (m.getMeetingDate() == null) {
                throw new IllegalArgumentException("请先设置会议日期");
            }
            List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(meetingId);
            // 0728 用户定：微信通知留痕后也可开始会议，与前端解锁条件一致（送达全体 或 有微信留痕）。
            // 微信路径只写通知日志、不建送达记录，原先这里必查送达记录导致微信通知后开会报错。
            boolean wechatNotified = notificationLogRepo.findByMeetingIdOrderBySentAtAsc(meetingId).stream()
                    .anyMatch(l -> l != null && "wechat".equals(l.getChannel()));
            if (deliveries.isEmpty()) {
                if (!wechatNotified) {
                    throw new IllegalArgumentException("请先选择应参会人员并发送会议通知");
                }
            } else {
                boolean allNoticesDone = deliveries.stream().allMatch(MeetingDelivery::getNoticeDelivered);
                boolean allMaterialsDone = deliveries.stream().allMatch(MeetingDelivery::getMaterialDelivered);
                if ((!allNoticesDone || !allMaterialsDone) && !wechatNotified) {
                    throw new IllegalArgumentException("通知和材料尚未全部送达，不可开始会议");
                }
            }
            // Initialize record
            MeetingRecord record = initRecord(m);
            // 仅微信通知（无送达记录）时，参会名单从送达记录生成不出来，兜底补齐为全体委员——
            // 微信群发即全员可见；否则签到名单为空，结束判定 0/0 会直接判会议不成立。
            if (deliveries.isEmpty()) {
                List<Long> rosterIds = attendanceRepo.findByRecordId(record.getId()).stream()
                        .map(a -> a.getUserRole().getId())
                        .toList();
                for (UserRoleEntity member : findCommitteeMembers(m.getCommunity().getId())) {
                    if (!rosterIds.contains(member.getId())) {
                        attendanceRepo.save(RecordAttendance.builder()
                                .record(record).userRole(member).signedIn(false).signed(false).build());
                    }
                }
            }
            if (topicRepo.findByRecordIdOrderBySortOrder(record.getId()).isEmpty()) {
                throw new IllegalArgumentException("请先补充会议议题；快速模式需要围绕预设议题进行录音识别");
            }
            m.setStage(MeetingStage.ongoing);
            m.setMeetingMode(MeetingMode.quick);

            // 会议开始：清空准备阶段的"确认参会"(RSVP)，改为会上逐个签到。
            // 这样 signedIn 在 ongoing 阶段表示"实际入会签到"，签到进度从 0 开始随委员陆续签到增加，
            // 法定人数/表决资格/纪要出席名单也都以真实到会人数为准。declined(无法参会) 保留不动。
            List<RecordAttendance> startAtts = attendanceRepo.findByRecordId(record.getId());
            for (RecordAttendance a : startAtts) {
                if (Boolean.TRUE.equals(a.getSignedIn())) {
                    a.setSignedIn(false);
                    a.setSigned(false);
                }
                a.setAttendanceMode(null);
                a.setProxySignAuthorized(false);
                a.setProxySignAuthorizedAt(null);
            }
            attendanceRepo.saveAll(startAtts);

        } else if ("end".equals(action)) {
            if (m.getStage() != MeetingStage.ongoing) {
                throw new IllegalArgumentException("仅进行中的会议可以结束");
            }
            Map<String, Object> er = evaluateEndResult(m);
            String level = (String) er.get("level");
            List<String> notes = (List<String>) er.get("notes");

            if ("invalid".equals(level)) {
                m.setCompliance(ComplianceStatus.invalid);
                m.setStage(MeetingStage.ended);
                // 测试阶段：判定无效即可，不自动重新开会（不是每次都全员签到）
                // createRecreation(m);
            } else if ("flawed".equals(level)) {
                m.setCompliance(ComplianceStatus.flawed);
                m.setStage(MeetingStage.ended);
            } else {
                m.setCompliance(ComplianceStatus.valid);
                m.setStage(MeetingStage.ended);
            }
            // Initialize publish
            if (m.getCompliance() != ComplianceStatus.invalid) {
                MeetingPublish pub = publishRepo.findByMeetingId(meetingId)
                        .orElse(MeetingPublish.builder()
                                .meeting(m)
                                .published(false)
                                .withdrawn(false)
                                .build());
                publishRepo.save(pub);
            }
        }
        meetingRepo.save(m);
    }

    /** 主任手动修正会议有效性判定（自动判定有误时纠正） */
    @Transactional
    public void setCompliance(Long meetingId, String status) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.ended) {
            throw new IllegalArgumentException("仅已结束的会议可以修正有效性判定");
        }
        ComplianceStatus cs;
        try {
            cs = ComplianceStatus.valueOf(status);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的有效性取值：" + status);
        }
        m.setCompliance(cs);
        // 改为"非无效"时，确保有公示记录，便于后续发起公示
        if (cs != ComplianceStatus.invalid) {
            publishRepo.findByMeetingId(meetingId).orElseGet(() ->
                    publishRepo.save(MeetingPublish.builder()
                            .meeting(m).published(false).withdrawn(false).build()));
        }
        meetingRepo.save(m);
    }

    // ===== Delivery =====
    @Transactional
    public void toggleDelivery(Long meetingId, Long userRoleId, String field) {
        MeetingDelivery d = deliveryRepo.findByMeetingIdAndUserRoleId(meetingId, userRoleId)
                .orElseThrow(() -> new IllegalArgumentException("送达记录不存在"));
        if ("notice".equals(field)) d.setNoticeDelivered(!d.getNoticeDelivered());
        else if ("material".equals(field)) d.setMaterialDelivered(!d.getMaterialDelivered());
        deliveryRepo.save(d);
        markNotifiedIfComplete(meetingId);
    }

    /** 委员打开会议详情 → 回写自己的已读时间（仅对已送达内容、且尚未读时记一次）。 */
    @Transactional
    public void markDeliveryRead(Long meetingId) {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        deliveryRepo.findByMeetingIdAndUserRoleId(meetingId, ur.getId()).ifPresent(d -> {
            boolean changed = false;
            if (Boolean.TRUE.equals(d.getNoticeDelivered()) && d.getNoticeReadAt() == null) {
                d.setNoticeReadAt(LocalDateTime.now());
                changed = true;
            }
            if (Boolean.TRUE.equals(d.getMaterialDelivered()) && d.getMaterialReadAt() == null) {
                d.setMaterialReadAt(LocalDateTime.now());
                changed = true;
            }
            if (changed) deliveryRepo.save(d);
        });
    }

    @Transactional
    public void sendAll(Long meetingId, List<Long> memberIds) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (meeting.getStage() != MeetingStage.preparing) {
            throw new IllegalArgumentException("仅准备阶段可以发送会议通知");
        }
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("请选择需要通知并应参会的委员");
        }

        UserRoleEntity initiator = SecurityUtils.getCurrentUserRole();
        LocalDateTime now = LocalDateTime.now();
        List<UserRoleEntity> members = resolveMeetingMembers(meeting.getCommunity().getId(), memberIds);
        deliveryRepo.deleteByMeetingId(meetingId);
        List<MeetingDelivery> deliveries = members.stream()
                .map(member -> MeetingDelivery.builder()
                        .meeting(meeting)
                        .userRole(member)
                        .noticeDelivered(true)
                        .materialDelivered(true)
                        .build())
                .collect(Collectors.toList());
        deliveryRepo.saveAll(deliveries);
        markNotifiedIfComplete(meetingId);
        // 记录发送人：全部送达置 notifiedAt 后，同步记录当前主任/副主任"姓名·角色"（谁发的通知）
        if (initiator != null && meeting.getNotifiedAt() != null) {
            meeting.setNotifiedByName(initiator.getRealName() + "·" + initiator.getRole());
            meetingRepo.save(meeting);
        }
        // 追加通知历史记录
        notificationLogRepo.save(MeetingNotificationLog.builder()
                .meeting(meeting)
                .sentAt(now)
                .sentByName(initiator != null ? initiator.getRealName() + "·" + initiator.getRole() : null)
                .channel("app")
                .build());

        // 发起人（当前主任/副主任）发送会议通知时即自动"确认参会"，计入确认参会人数（自动为 1），无需再手动确认。
        if (initiator != null) {
            MeetingRecord record = recordRepo.findByMeetingId(meetingId).orElseGet(() -> initRecord(meeting));
            RecordAttendance att = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), initiator.getId())
                    .orElseGet(() -> RecordAttendance.builder()
                            .record(record).userRole(initiator).signedIn(false).signed(false).build());
            if (!Boolean.TRUE.equals(att.getSignedIn())) {
                att.setSignedIn(true);
                att.setOperator(initiator);
                att.setIsProxy(false);
                att.setOperatedAt(now);
                attendanceRepo.save(att);
            }
        }
    }

    @Transactional
    public void markWechatNotified(Long meetingId) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (meeting.getStage() != MeetingStage.preparing) {
            throw new IllegalArgumentException("仅准备阶段可以记录微信通知");
        }
        UserRoleEntity initiator = SecurityUtils.getCurrentUserRole();
        notificationLogRepo.save(MeetingNotificationLog.builder()
                .meeting(meeting)
                .sentAt(LocalDateTime.now())
                .sentByName(initiator != null ? initiator.getRealName() + "·" + initiator.getRole() : null)
                .channel("wechat")
                .build());
    }

    /** 清空会议通知记录：删通知历史 + 送达记录，重置 notifiedAt/notifiedByName，回到"未通知"状态。
     *  0728 用户定为正式功能（通知内容有误需重发时用）；因会一并清掉"谁何时通知了委员"的留痕，前端确认弹窗须明确提示后果。 */
    @Transactional
    public void clearNotifications(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        notificationLogRepo.deleteAll(notificationLogRepo.findByMeetingIdOrderBySentAtAsc(meetingId));
        deliveryRepo.deleteByMeetingId(meetingId);
        m.setNotifiedAt(null);
        m.setNotifiedByName(null);
        meetingRepo.save(m);
    }

    private List<MeetingDetailVO.NotificationLogVO> buildNotificationLogs(Long meetingId) {
        return notificationLogRepo.findByMeetingIdOrderBySentAtAsc(meetingId).stream()
                .map(l -> {
                    MeetingDetailVO.NotificationLogVO vo = new MeetingDetailVO.NotificationLogVO();
                    vo.setSentAt(l.getSentAt().toString());
                    vo.setSentByName(l.getSentByName());
                    vo.setChannel(l.getChannel() == null ? "app" : l.getChannel());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 全部通知送达后记录"通知完成"时间，触发重大字段锁定（规则8）。 */
    private void markNotifiedIfComplete(Long meetingId) {
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(meetingId);
        boolean allNotified = !deliveries.isEmpty()
                && deliveries.stream().allMatch(MeetingDelivery::getNoticeDelivered);
        CommitteeMeeting m = meetingRepo.findById(meetingId).orElse(null);
        if (m == null) return;
        if (allNotified) {
            m.setNotifiedAt(LocalDateTime.now());
            meetingRepo.save(m);
        } else if (!allNotified && m.getNotifiedAt() != null) {
            // 撤回送达 → 解除锁定
            m.setNotifiedAt(null);
            m.setNotifiedByName(null);
            meetingRepo.save(m);
        }
    }

    // ===== Attendance (sign-in/sign) =====
    @Transactional
    public void toggleAttendance(Long meetingId, Long userRoleId, String field) {
        MeetingRecord record = getRecord(meetingId);
        RecordAttendance a = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), userRoleId)
                .orElseThrow(() -> new IllegalArgumentException("参会记录不存在"));
        if ("signedIn".equals(field)) {
            a.setSignedIn(true);
            a.setOperator(SecurityUtils.getCurrentUserRole());
            a.setIsProxy(!SecurityUtils.getCurrentUserId().equals(userRoleId));
            a.setOperatedAt(LocalDateTime.now());
        } else if ("signed".equals(field) && a.getSignedIn()) {
            a.setSigned(true);
        }
        attendanceRepo.save(a);
    }

    /** 主持人修正参会状态（0722，名单弹窗下拉）：onsite=已签到 / remote=线上参会 / declined=请假缺席 / none=未参会。
     *  记 operator/isProxy/operatedAt 留痕（代改他人时 isProxy=true）。 */
    @Transactional
    public void setAttendanceStatus(Long meetingId, Long userRoleId, String value) {
        MeetingRecord record = getRecord(meetingId);
        RecordAttendance a = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), userRoleId)
                .orElseThrow(() -> new IllegalArgumentException("参会记录不存在"));
        // 已签到的不允许改回未签到类状态（0723 用户定）：签到是既成事实，只许在现场/线上之间纠错，
        // 不许抹掉——避免会后把真实到会记录改没，影响记录/纪要的实到人数与表决合法性。
        if (Boolean.TRUE.equals(a.getSignedIn()) && ("declined".equals(value) || "none".equals(value))) {
            throw new IllegalArgumentException("该委员已签到，不能改为未签到状态");
        }
        if ("onsite".equals(value)) {
            a.setSignedIn(true); a.setDeclined(false); a.setAttendanceMode("onsite");
        } else if ("remote".equals(value)) {
            a.setSignedIn(true); a.setDeclined(false); a.setAttendanceMode("remote");
        } else if ("declined".equals(value)) {
            a.setSignedIn(false); a.setDeclined(true); a.setAttendanceMode(null);
        } else if ("none".equals(value)) {
            a.setSignedIn(false); a.setDeclined(false); a.setAttendanceMode(null);
        } else {
            throw new IllegalArgumentException("不支持的参会状态");
        }
        UserRoleEntity operator = SecurityUtils.getCurrentUserRole();
        a.setOperator(operator);
        a.setIsProxy(operator != null && !operator.getId().equals(userRoleId));
        a.setOperatedAt(LocalDateTime.now());
        attendanceRepo.save(a);
    }

    @Transactional
    public void selfToggle(Long meetingId, String field) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        // 准备阶段可能还没有 MeetingRecord，需要按需创建
        MeetingRecord record = recordRepo.findByMeetingId(meetingId).orElse(null);
        if (record == null) {
            record = initRecord(meeting);
        }
        Long urId = SecurityUtils.getCurrentUserId();
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        RecordAttendance a = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), urId)
                .orElse(null);
        if (a == null) {
            // 用户不在参会名单中 → 自动补入（通知对象即应参会人）
            a = RecordAttendance.builder()
                    .record(record)
                    .userRole(ur)
                    .signedIn(false)
                    .signed(false)
                    .build();
            a = attendanceRepo.save(a);
        }
        if ("signedIn".equals(field)) {
            a.setSignedIn(true);
            a.setDeclined(false);
            a.setOperator(ur);
            a.setIsProxy(false);
            a.setOperatedAt(LocalDateTime.now());
        } else if ("declined".equals(field)) {
            // 无法参会 → 标记因故缺席，并取消签到
            a.setDeclined(true);
            a.setSignedIn(false);
            a.setAttendanceMode(null);
            a.setProxySignAuthorized(false);
            a.setProxySignAuthorizedAt(null);
            a.setOperator(ur);
            a.setIsProxy(false);
            a.setOperatedAt(LocalDateTime.now());
        } else if ("cancel".equals(field)) {
            // 取消参会 → 回到未响应（既不确认也不缺席）
            a.setSignedIn(false);
            a.setDeclined(false);
            a.setAttendanceMode(null);
            a.setProxySignAuthorized(false);
            a.setProxySignAuthorizedAt(null);
            a.setOperator(ur);
            a.setIsProxy(false);
            a.setOperatedAt(LocalDateTime.now());
        } else if ("signed".equals(field) && a.getSignedIn()) {
            a.setSigned(true);
        }
        attendanceRepo.save(a);
    }

    @Transactional
    public void selfAttend(Long meetingId, String mode, boolean authorizeProxySign) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        // 线上会议也各自签到（0725 用户定）：个人签到一律记远程参会；主持人的统一登记仅作未签到者的兜底代录
        if (meeting.getMeetingMethod() == com.ywh.enums.MeetingMethod.online) {
            mode = "remote";
        }
        if (!"onsite".equals(mode) && !"remote".equals(mode)) {
            throw new IllegalArgumentException("参会方式不正确");
        }
        MeetingRecord record = recordRepo.findByMeetingId(meetingId).orElseGet(() -> initRecord(meeting));
        Long userRoleId = SecurityUtils.getCurrentUserId();
        UserRoleEntity userRole = SecurityUtils.getCurrentUserRole();
        RecordAttendance attendance = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), userRoleId)
                .orElseGet(() -> RecordAttendance.builder()
                        .record(record).userRole(userRole).signedIn(false).signed(false).build());
        attendance.setSignedIn(true);
        attendance.setDeclined(false);
        attendance.setAttendanceMode(mode);
        attendance.setProxySignAuthorized(authorizeProxySign);
        attendance.setProxySignAuthorizedAt(authorizeProxySign ? LocalDateTime.now() : null);
        attendance.setOperator(userRole);
        attendance.setIsProxy(false);
        attendance.setOperatedAt(LocalDateTime.now());
        attendanceRepo.save(attendance);
    }

    @Transactional
    public void signAll(Long meetingId) {
        MeetingRecord record = getRecord(meetingId);
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        for (RecordAttendance a : attendances) {
            if (!Boolean.TRUE.equals(a.getSignedIn())) continue;
            boolean remote = "remote".equals(a.getAttendanceMode());
            if (!remote || Boolean.TRUE.equals(a.getProxySignAuthorized())) {
                a.setSigned(true);
            }
        }
        attendanceRepo.saveAll(attendances);
        if (record.getHasMajorIssue()) {
            record.setJuweiSigned(true);
            recordRepo.save(record);
        }
    }

    @Transactional
    public void setOnlineAttendance(Long meetingId, List<Long> presentMemberIds) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (meeting.getMeetingMethod() != com.ywh.enums.MeetingMethod.online) {
            throw new IllegalArgumentException("仅线上会议可由主持人统一登记参会情况");
        }
        if (meeting.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅进行中的会议可以登记参会情况");
        }
        MeetingRecord record = getRecord(meetingId);
        Set<Long> present = new HashSet<>(Optional.ofNullable(presentMemberIds).orElse(Collections.emptyList()));
        UserRoleEntity operator = SecurityUtils.getCurrentUserRole();
        LocalDateTime now = LocalDateTime.now();
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        for (RecordAttendance attendance : attendances) {
            boolean isPresent = present.contains(attendance.getUserRole().getId());
            attendance.setSignedIn(isPresent);
            attendance.setDeclined(!isPresent);
            attendance.setSigned(false);
            attendance.setOperator(operator);
            attendance.setIsProxy(true);
            attendance.setOperatedAt(now);
        }
        attendanceRepo.saveAll(attendances);
    }

    // ===== Topics & Votes =====
    @Transactional
    public RecordTopic addTopic(Long meetingId, String title, String type,
                                 String decisionType, String options, Boolean realNameVote, String content) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        // 规则8：议题为进行中现场新增
        if (m.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅会议进行中可新增议题");
        }
        TopicType tType = parseAgendaType(type);
        // 规则6：重大事项不可现场新增，应列入会前通知或下次会议议题
        if (tType == TopicType.major) {
            throw new IllegalArgumentException("重大事项不可现场新增表决；请列入会前通知或下次会议议题");
        }
        if (tType == TopicType.notice || tType == TopicType.discussion) {
            decisionType = "none";
            options = null;
        }
        MeetingRecord record = getRecord(meetingId);
        String dt = decisionType != null && !decisionType.isEmpty() ? decisionType : "simple";
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        RecordTopic topic = RecordTopic.builder()
                .record(record)
                .title(title)
                .type(tType)
                .decisionType(dt)
                .optionsJson(options)
                .sortOrder((int) topicRepo.findByRecordIdOrderBySortOrder(record.getId()).size() + 1)
                .source("live")
                .createdById(ur != null ? ur.getId() : null)
                .createdByName(ur != null ? ur.getRealName() : null)
                .realNameVote(Boolean.TRUE.equals(realNameVote))
                .content(tType == TopicType.notice ? content : null)
                .notified(false)
                .build();
        return topicRepo.save(topic);
    }

    // ===== 通报类议题：正文查看 / 已宣读 → 已通报 =====
    private RecordTopic requireTopic(Long meetingId, Long topicId) {
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        if (topic.getRecord() == null || !topic.getRecord().getId().equals(getRecord(meetingId).getId())) {
            throw new IllegalArgumentException("议题不属于本次会议");
        }
        return topic;
    }

    private Set<Long> parseViewedBy(String json) {
        if (json == null || json.isBlank()) return new LinkedHashSet<>();
        try {
            return new LinkedHashSet<>(objectMapper.readValue(json, new TypeReference<List<Long>>() {}));
        } catch (Exception e) { return new LinkedHashSet<>(); }
    }

    private String writeViewedBy(Set<Long> ids) {
        try { return objectMapper.writeValueAsString(new ArrayList<>(ids)); }
        catch (Exception e) { return null; }
    }

    /** 通报议题：记录当前用户已查看；若会议参会名单中的全体委员都看过，则自动标记已通报。 */
    @Transactional
    public void markNoticeViewed(Long meetingId, Long topicId) {
        RecordTopic topic = requireTopic(meetingId, topicId);
        if (topic.getType() != TopicType.notice) return;
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur == null) return;
        Set<Long> viewed = parseViewedBy(topic.getViewedByJson());
        viewed.add(ur.getId());
        topic.setViewedByJson(writeViewedBy(viewed));
        List<Long> attendees = attendanceRepo.findByRecordId(topic.getRecord().getId()).stream()
                .map(a -> a.getUserRole().getId()).collect(Collectors.toList());
        if (!attendees.isEmpty() && viewed.containsAll(attendees)) {
            topic.setNotified(true);
        }
        topicRepo.save(topic);
    }

    /** 通报议题：有人「已宣读」→ 直接标记已通报。 */
    @Transactional
    public void markNoticeRead(Long meetingId, Long topicId) {
        RecordTopic topic = requireTopic(meetingId, topicId);
        if (topic.getType() != TopicType.notice) return;
        topic.setNotified(true);
        topicRepo.save(topic);
    }

    @Transactional
    public void removeTopic(Long meetingId, Long topicId) {
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        // 先清该议题的意见，避免外键约束删除失败
        opinionRepo.deleteAll(opinionRepo.findByTopicId(topicId));
        topicRepo.delete(topic);
    }

    /** 进行中人工修改议题名称（主任/副主任）：AI 推测标题不准、预设标题写错时改名。 */
    public void renameTopic(Long meetingId, Long topicId, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("议题名称不能为空");
        }
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅会议进行中可修改议题名称");
        }
        MeetingRecord record = getRecord(meetingId);
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        if (topic.getRecord() == null || !topic.getRecord().getId().equals(record.getId())) {
            throw new IllegalArgumentException("议题不属于本次会议");
        }
        topic.setTitle(title.trim());
        topicRepo.save(topic);
    }

    /** 结束表决（主任/副主任）：该表决议题票数揭晓、公布结果。此前对委员隐藏票数明细（防从众）。 */
    @Transactional
    public void closeVote(Long meetingId, Long topicId) {
        RecordTopic topic = requireTopic(meetingId, topicId);
        if (!isVoteTopic(topic)) {
            throw new IllegalArgumentException("该议题无需表决");
        }
        topic.setVoteClosed(true);
        topicRepo.save(topic);
    }

    @Transactional
    public void vote(Long meetingId, Long topicId, String choice, Long selectedId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅会议进行中可投票");
        }
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        if (Boolean.TRUE.equals(topic.getVoteClosed())) {
            throw new IllegalArgumentException("该议题表决已结束，无法再投票");
        }
        Long urId = SecurityUtils.getCurrentUserId();
        MeetingRecord record = topic.getRecord();
        RecordAttendance attendance = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), urId)
                .orElseThrow(() -> new IllegalArgumentException("请先确认参会后再投票"));
        if (!Boolean.TRUE.equals(attendance.getSignedIn())) {
            throw new IllegalArgumentException("请先确认参会后再投票");
        }
        // 已投过则覆盖更新：表决未结束前允许改票（voteClosed 已在上方拦截，主任结束表决后走不到这里）
        TopicVote existing = voteRepo.findByTopicIdAndUserRoleId(topicId, urId).orElse(null);
        TopicVote vote = (existing != null) ? existing : TopicVote.builder()
                .topic(topic)
                .userRole(SecurityUtils.getCurrentUserRole())
                .isProxy(false)
                .build();
        vote.setOperator(SecurityUtils.getCurrentUserRole());
        vote.setOperatedAt(LocalDateTime.now());
        // 改票时先清掉另一种存储，避免 simple/multi 切换后残留旧值
        vote.setChoice(null);
        vote.setSelectedId(null);
        // simple: 存 choice（for_vote/against/abstain）; multi_choice: 存 selectedId
        if (selectedId != null) {
            vote.setSelectedId(selectedId);
        } else if (choice != null && !choice.isEmpty()) {
            vote.setChoice(VoteChoice.valueOf(choice));
        }
        voteRepo.save(vote);
    }

    /** 撤回本人投票（表决未结束前）：删除本人对该议题的投票，回到"未投"。 */
    @Transactional
    public void retractVote(Long meetingId, Long topicId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅会议进行中可撤回投票");
        }
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        if (Boolean.TRUE.equals(topic.getVoteClosed())) {
            throw new IllegalArgumentException("该议题表决已结束，无法撤回");
        }
        Long urId = SecurityUtils.getCurrentUserId();
        voteRepo.findByTopicIdAndUserRoleId(topicId, urId).ifPresent(voteRepo::delete);
    }

    // ===== 议题意见 =====

    /** 本会议全部意见（平铺，前端按 topicId 分组）；任何阶段可查看。 */
    public List<Map<String, Object>> listOpinions(Long meetingId) {
        MeetingRecord record = getRecord(meetingId);
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        boolean chair = isChair(ur);
        return opinionRepo.findByTopicRecordIdOrderByCreatedAtAsc(record.getId()).stream()
                .map(op -> opinionToMap(op, ur, chair))
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> addOpinion(Long meetingId, Long topicId, String content, String source) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("意见内容不能为空");
        }
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅会议进行中可发表意见");
        }
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        MeetingRecord record = topic.getRecord();
        if (record == null || !record.getId().equals(getRecord(meetingId).getId())) {
            throw new IllegalArgumentException("议题不属于本次会议");
        }
        Long urId = SecurityUtils.getCurrentUserId();
        RecordAttendance attendance = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), urId)
                .orElseThrow(() -> new IllegalArgumentException("请先签到后再发表意见"));
        if (!Boolean.TRUE.equals(attendance.getSignedIn())) {
            throw new IllegalArgumentException("请先签到后再发表意见");
        }
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        String src = ("voice".equals(source) || "ai".equals(source)) ? source : "text";
        TopicOpinion op = TopicOpinion.builder()
                .topic(topic)
                .userRole(ur)
                .speakerName(ur.getRealName())
                .content(content.trim())
                .source(src)
                .build();
        opinionRepo.save(op);
        return opinionToMap(op, ur, isChair(ur));
    }

    /** 修改意见内容：仅本人。 */
    @Transactional
    public void updateOpinion(Long meetingId, Long opinionId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("意见内容不能为空");
        }
        TopicOpinion op = findMeetingOpinion(meetingId, opinionId);
        Long urId = SecurityUtils.getCurrentUserId();
        boolean own = op.getUserRole() != null && op.getUserRole().getId().equals(urId);
        // 主任/副主任可纠正 AI 提炼偏差（仅未认领的现场意见）；委员本人意见仍只能本人改
        boolean chairFixAi = "ai".equals(op.getSource()) && op.getUserRole() == null
                && isChair(SecurityUtils.getCurrentUserRole());
        if (!own && !chairFixAi) {
            throw new IllegalArgumentException("只能修改自己的意见");
        }
        op.setContent(content.trim());
        opinionRepo.save(op);
    }

    /** 删除意见：本人或主任/副主任。 */
    @Transactional
    public void removeOpinion(Long meetingId, Long opinionId) {
        TopicOpinion op = findMeetingOpinion(meetingId, opinionId);
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        boolean own = op.getUserRole() != null && op.getUserRole().getId().equals(ur.getId());
        if (!own && !isChair(ur)) {
            throw new IllegalArgumentException("只能删除自己的意见");
        }
        opinionRepo.delete(op);
    }

    private TopicOpinion findMeetingOpinion(Long meetingId, Long opinionId) {
        TopicOpinion op = opinionRepo.findById(opinionId)
                .orElseThrow(() -> new IllegalArgumentException("意见不存在"));
        MeetingRecord record = op.getTopic() != null ? op.getTopic().getRecord() : null;
        if (record == null || !record.getId().equals(getRecord(meetingId).getId())) {
            throw new IllegalArgumentException("意见不属于本次会议");
        }
        return op;
    }

    private Map<String, Object> opinionToMap(TopicOpinion op, UserRoleEntity current, boolean chair) {
        boolean own = op.getUserRole() != null && op.getUserRole().getId().equals(current.getId());
        boolean aiUnclaimed = "ai".equals(op.getSource()) && op.getUserRole() == null;
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", op.getId());
        vo.put("topicId", op.getTopic().getId());
        vo.put("userRoleId", op.getUserRole() != null ? op.getUserRole().getId() : null);
        vo.put("name", op.getUserRole() != null ? op.getUserRole().getRealName()
                : (op.getSpeakerName() != null ? op.getSpeakerName() : "现场发言"));
        vo.put("role", op.getUserRole() != null ? op.getUserRole().getRole().name() : null);
        vo.put("content", op.getContent());
        vo.put("source", op.getSource());
        vo.put("isSelf", own);
        vo.put("canEdit", own || (chair && aiUnclaimed)); // 主任可纠正 AI 提炼偏差（仅未认领的）
        vo.put("canDelete", own || chair);
        vo.put("claimable", aiUnclaimed); // AI 提炼且未归属 → 可"是我说的"认领
        vo.put("createdAt", op.getCreatedAt() != null ? op.getCreatedAt().toString() : null);
        // 作者对本议题的表决结果：意见姓名旁带「同意/不同意/弃权/所选选项」标签。
        // 不区分实名/匿名表决——意见本就署名发表，且业委会表决记录归档要记名；实名开关(规则5)
        // 仍只控制 voterChoices 全员名单是否下发，这里只带"这条意见作者自己"的一票。
        if (op.getUserRole() != null && isVoteTopic(op.getTopic())) {
            voteRepo.findByTopicIdAndUserRoleId(op.getTopic().getId(), op.getUserRole().getId()).ifPresent(v -> {
                if (v.getSelectedId() != null) {
                    Map<String, Object> opt = findOption(parseTopicOptions(op.getTopic()), v.getSelectedId());
                    if (opt != null) vo.put("voteLabel", opt.get("label"));
                } else if (v.getChoice() != null) {
                    vo.put("voteChoice", v.getChoice().name());
                }
            });
        }
        return vo;
    }

    /**
     * 意见 AI 助手：mode=polish 把已有意见润色得正式规范 / mode=draft 按委员口头描述代拟发言。
     * 只生成文本回给前端供确认/修改，不入库。返回 { text, tokens }。
     */
    public Map<String, Object> assistOpinion(Long meetingId, Long topicId, String mode, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("请先说说或写下你的想法");
        }
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        MeetingRecord record = topic.getRecord();
        if (record == null || !record.getId().equals(getRecord(meetingId).getId())) {
            throw new IllegalArgumentException("议题不属于本次会议");
        }
        DoubaoOcrService svc = ocrServiceProvider.getIfAvailable();
        if (svc == null) throw new IllegalArgumentException("AI 助手未启用");
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        try {
            return svc.opinionAssistSync("draft".equals(mode) ? "draft" : "polish",
                    topic.getTitle(), topic.getType() != null ? topic.getType().name() : "",
                    ur.getRole().name(), text.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(svc.serviceUnreachable(e)
                    ? "AI 助手服务未启动，请稍后再试" : "AI 助手开小差了，请重试");
        }
    }

    /**
     * 纪要生成顺带提炼的现场意见入库（source='ai'，展示带"现场·AI"标）。
     *
     * 提炼语义上是"从录音一次性抽取"：只要本会议已经有过 AI 提炼的意见（无论是否被认领），
     * 就认为已提炼过，**再次生成纪要（markdown）时不重复提炼**——从根上杜绝"重生成产生近似重复条目"
     * （大模型重跑同一段转写会换措辞，字符去重挡不住语义近似，故直接不重跑提炼）。
     * 首次提炼时对本轮结果做议题内精确/近似去重（防大模型一次吐两条几乎一样的）。
     * speaker 是 S1/说话人N/未知 等编号时不落名字（前端显示"现场发言·待认领"）。
     * 主任若想重新提炼（如补录了录音），先删掉现有 AI 意见再生成即可。
     */
    @Transactional
    public void saveAiOpinions(Long meetingId, QuickPolishVO vo) {
        if (vo == null || vo.getTopics() == null || vo.getTopics().isEmpty()) return;
        MeetingRecord record = getRecord(meetingId);
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        if (topics.isEmpty()) return;

        List<TopicOpinion> existing = opinionRepo.findByTopicRecordIdOrderByCreatedAtAsc(record.getId());
        // 已提炼过（存在任一 source='ai' 的意见）→ 视为本次录音的意见已抽取，重生成纪要不再重复提炼
        boolean alreadyExtracted = existing.stream().anyMatch(op -> "ai".equals(op.getSource()));
        if (alreadyExtracted) return;

        // 首次提炼：议题内去重（精确 + 近似），防大模型一次返回多条几乎一样的意见
        Map<Long, List<String>> insertedTexts = new HashMap<>();
        for (QuickPolishVO.TopicSummary ts : vo.getTopics()) {
            RecordTopic topic = resolveTopicByRef(topics, ts.getRef());
            if (topic == null || ts.getOpinions() == null) continue;
            List<String> topicTexts = insertedTexts.computeIfAbsent(topic.getId(), k -> new ArrayList<>());
            for (QuickPolishVO.OpinionDraft draft : ts.getOpinions()) {
                String text = draft.getText() == null ? "" : draft.getText().trim();
                if (text.isEmpty() || isNearDuplicateOpinion(text, topicTexts)) continue;
                opinionRepo.save(TopicOpinion.builder()
                        .topic(topic)
                        .userRole(null)
                        .speakerName(normalizeSpeakerName(draft.getSpeaker()))
                        .content(text)
                        .source("ai")
                        .build());
                topicTexts.add(text);
            }
        }
    }

    /** 意见文本是否与已有任一条精确/近似重复（字符 bigram Jaccard ≥ 0.72 即视为同一条发言的不同措辞）。 */
    private boolean isNearDuplicateOpinion(String text, List<String> existing) {
        if (existing == null || existing.isEmpty()) return false;
        Set<String> a = opinionBigrams(text);
        for (String other : existing) {
            if (text.equals(other)) return true;
            Set<String> b = opinionBigrams(other);
            if (a.isEmpty() || b.isEmpty()) continue;
            int inter = 0;
            for (String g : a) if (b.contains(g)) inter++;
            double jaccard = (double) inter / (a.size() + b.size() - inter);
            if (jaccard >= 0.72) return true;
        }
        return false;
    }

    /** 归一化后取字符 2-gram 集合：只留中文/字母/数字，去标点空白，抹平措辞里的虚词标点差异。 */
    private Set<String> opinionBigrams(String s) {
        if (s == null) return Collections.emptySet();
        String norm = s.replaceAll("[^\\p{IsHan}a-zA-Z0-9]", "");
        Set<String> grams = new HashSet<>();
        if (norm.length() < 2) { if (!norm.isEmpty()) grams.add(norm); return grams; }
        for (int i = 0; i < norm.length() - 1; i++) grams.add(norm.substring(i, i + 2));
        return grams;
    }

    /** ref 兼容三种口径：topicId（非上下文路径）、议题序号 1..n（compact 上下文路径）、议题标题。 */
    private RecordTopic resolveTopicByRef(List<RecordTopic> topics, String ref) {
        if (ref == null || ref.isBlank()) return null;
        String r = ref.trim();
        try {
            long n = Long.parseLong(r);
            for (RecordTopic t : topics) {
                if (t.getId().equals(n)) return t;
            }
            if (n >= 1 && n <= topics.size()) return topics.get((int) n - 1);
        } catch (NumberFormatException ignored) { /* 非数字，走标题匹配 */ }
        for (RecordTopic t : topics) {
            if (t.getTitle() != null && (t.getTitle().equals(r) || r.contains(t.getTitle()))) return t;
        }
        return null;
    }

    /** S1/说话人2/未知发言人 等编号不算名字；真实姓名原样保留（是否本人由认领确定，不自动绑账号）。 */
    private String normalizeSpeakerName(String speaker) {
        if (speaker == null) return null;
        String s = speaker.trim();
        if (s.isEmpty()) return null;
        if (s.matches("(?i)S\\d{1,3}") || s.matches("(说话人|发言人)\\s*\\d{1,3}") || s.contains("未知")) return null;
        return s.length() > 20 ? null : s;
    }

    /**
     * 认领/指派 AI 提炼的现场意见归属：不带 userRoleId 是本人认领；带 userRoleId 仅主任/副主任可指派。
     * 只有 source='ai' 且未归属的意见可认领。
     */
    @Transactional
    public Map<String, Object> claimOpinion(Long meetingId, Long opinionId, Long assignUserRoleId) {
        TopicOpinion op = findMeetingOpinion(meetingId, opinionId);
        if (!"ai".equals(op.getSource())) throw new IllegalArgumentException("只有 AI 提炼的现场意见可以认领");
        if (op.getUserRole() != null) throw new IllegalArgumentException("这条意见已有归属");
        UserRoleEntity current = SecurityUtils.getCurrentUserRole();
        UserRoleEntity target = current;
        if (assignUserRoleId != null && !assignUserRoleId.equals(current.getId())) {
            if (!isChair(current)) throw new IllegalArgumentException("只有主任/副主任可以指派归属");
            target = userRoleRepo.findById(assignUserRoleId)
                    .orElseThrow(() -> new IllegalArgumentException("成员不存在"));
        }
        op.setUserRole(target);
        op.setSpeakerName(target.getRealName());
        opinionRepo.save(op);
        return opinionToMap(op, current, isChair(current));
    }

    /** 意见语音输入：短语音同步转文字（直传 ocr-asr-service，不落盘）。服务未启用/连不上给友好提示。 */
    public String recognizeVoice(Long meetingId, String filename, String contentType, byte[] data) {
        getRecord(meetingId); // 校验会议存在
        DoubaoOcrService svc = ocrServiceProvider.getIfAvailable();
        if (svc == null) throw new IllegalArgumentException("语音识别未启用，请打字输入");
        try {
            return svc.asrRecognizeSync(filename, contentType, data);
        } catch (Exception e) {
            throw new IllegalArgumentException(svc.serviceUnreachable(e)
                    ? "语音识别服务未启动，请打字输入" : "语音识别失败，请重试或打字输入");
        }
    }

    @Transactional
    public void applyQuickConfirm(Long meetingId, QuickConfirmRequest req) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (meeting.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅进行中的会议可以确认快速识别结果");
        }
        if (meeting.getMeetingMode() != MeetingMode.quick) {
            meeting.setMeetingMode(MeetingMode.quick);
            meetingRepo.save(meeting);
        }
        MeetingRecord record = getRecord(meetingId);
        List<RecordAttendance> signedIn = attendanceRepo.findByRecordId(record.getId()).stream()
                .filter(RecordAttendance::getSignedIn)
                .collect(Collectors.toList());
        List<QuickConfirmRequest.TopicResult> results = Optional.ofNullable(req)
                .map(QuickConfirmRequest::getTopics)
                .orElse(Collections.emptyList())
                .stream()
                .filter(r -> r != null && Boolean.TRUE.equals(r.getConfirmed()) && r.getTopicId() != null)
                .collect(Collectors.toList());
        Map<Long, RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId()).stream()
                .collect(Collectors.toMap(RecordTopic::getId, t -> t));
        long voteTopicCount = topics.values().stream().filter(this::isVoteTopic).count();
        try {
            String confirmJson = objectMapper.writeValueAsString(req);
            record.setQuickConfirmJson(confirmJson);
            record.setQuickConfirmHash(sha256(confirmJson));
            recordRepo.save(record);
        } catch (Exception e) {
            throw new IllegalArgumentException("保存快速会议确认结果失败");
        }
        if (results.size() < topics.size()) {
            return;
        }
        if (signedIn.isEmpty()) {
            return;
        }
        if (voteTopicCount == 0) {
            return;
        }
        for (QuickConfirmRequest.TopicResult result : results) {
            RecordTopic topic = topics.get(result.getTopicId());
            if (topic == null) {
                throw new IllegalArgumentException("议题不属于本次会议");
            }
            // 快速模式：现场汇总票数存于 quickConfirmJson，与 app 内逐人投票并存——
            // 逐人投票是委员真实表态（myVote/实名留痕都靠它），不再清除；
            // 展示口径在 TopicVO 组装处按"逐桶取大"合并（现场汇总 vs 逐人计票）。
        }
    }

    private VoteChoice quickResultToChoice(String result) {
        if ("rejected".equals(result)) return VoteChoice.against;
        if ("abstain".equals(result) || "unclear".equals(result)) return VoteChoice.abstain;
        return VoteChoice.for_vote;
    }

    private Map<Long, QuickConfirmRequest.TopicResult> quickConfirmTopicMap(MeetingRecord record) {
        if (record == null || record.getQuickConfirmJson() == null || record.getQuickConfirmJson().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            QuickConfirmRequest req = objectMapper.readValue(record.getQuickConfirmJson(), QuickConfirmRequest.class);
            return Optional.ofNullable(req.getTopics()).orElse(Collections.emptyList()).stream()
                    .filter(t -> t != null && t.getTopicId() != null)
                    .collect(Collectors.toMap(QuickConfirmRequest.TopicResult::getTopicId, t -> t, (a, b) -> a));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /** 议题结果留痕：每题最近一次人工改动的展示文案（topicId → "张三 将结果改为「通过」 07-29 14:32"）。 */
    private Map<Long, String> latestResultAudit(MeetingRecord record) {
        if (record == null || record.getResultAuditJson() == null || record.getResultAuditJson().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            List<Map<String, Object>> entries = objectMapper.readValue(record.getResultAuditJson(),
                    new TypeReference<List<Map<String, Object>>>() {});
            Map<Long, String> out = new HashMap<>();
            for (Map<String, Object> e : entries) {   // 顺序追加，后写覆盖前写=留最近一次
                Long tid = e.get("topicId") == null ? null : Long.valueOf(String.valueOf(e.get("topicId")));
                if (tid != null && e.get("text") != null) out.put(tid, String.valueOf(e.get("text")));
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * 主任/副主任/秘书人工修改议题结果（0729 用户定）：允许改，但必须留痕。
     * 改动写入 quickConfirmJson 的该题 result（记录/纪要/公示取结果时同源生效），
     * 留痕追加到 resultAuditJson（谁、何时、由什么改成什么），永不覆盖历史。
     */
    @Transactional
    public void overrideTopicResult(Long meetingId, Long topicId, String result) {
        List<String> allowed = List.of("passed", "rejected", "invalid", "notified", "discussed");
        if (result == null || !allowed.contains(result)) throw new IllegalArgumentException("不支持的结果类型");
        MeetingRecord record = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议记录不存在"));
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        if (topic.getRecord() == null || !topic.getRecord().getId().equals(record.getId())) {
            throw new IllegalArgumentException("议题与会议不匹配");
        }
        boolean vote = isVoteTopic(topic);
        if (vote && !("passed".equals(result) || "rejected".equals(result) || "invalid".equals(result))) {
            throw new IllegalArgumentException("表决议题只能改为 通过/未通过/表决无效");
        }
        if (!vote && !("notified".equals(result) || "discussed".equals(result))) {
            throw new IllegalArgumentException("通报/讨论议题只能改为 已通报/已讨论");
        }
        // 1) 更新 quickConfirmJson 里该题的 result（无则补一条）
        QuickConfirmRequest req;
        try {
            req = record.getQuickConfirmJson() == null || record.getQuickConfirmJson().isBlank()
                    ? new QuickConfirmRequest()
                    : objectMapper.readValue(record.getQuickConfirmJson(), QuickConfirmRequest.class);
        } catch (Exception e) { req = new QuickConfirmRequest(); }
        if (req.getTopics() == null) req.setTopics(new ArrayList<>());
        QuickConfirmRequest.TopicResult tr = req.getTopics().stream()
                .filter(t -> t != null && topicId.equals(t.getTopicId())).findFirst().orElse(null);
        String prev = tr == null ? null : tr.getResult();
        if (tr == null) {
            tr = new QuickConfirmRequest.TopicResult();
            tr.setTopicId(topicId);
            req.getTopics().add(tr);
        }
        tr.setResult(result);
        tr.setConfirmed(true);
        // 通报类改「已通报」：同步议题 notified 标记，让所有展示口径一致
        if ("notified".equals(result) && !Boolean.TRUE.equals(topic.getNotified())) {
            topic.setNotified(true);
            topicRepo.save(topic);
        }
        // 2) 留痕（永不删改历史，逐条追加）
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        String byName = ur == null ? "未知操作人" : ur.getRealName();
        String at = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("topicId", topicId);
        entry.put("from", prev);
        entry.put("to", result);
        entry.put("byName", byName);
        entry.put("at", at);
        entry.put("text", byName + " 将结果改为「" + quickResultLabel(result) + "」 " + at);
        List<Map<String, Object>> audits;
        try {
            audits = record.getResultAuditJson() == null || record.getResultAuditJson().isBlank()
                    ? new ArrayList<>()
                    : objectMapper.readValue(record.getResultAuditJson(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) { audits = new ArrayList<>(); }
        audits.add(entry);
        try {
            record.setQuickConfirmJson(objectMapper.writeValueAsString(req));
            record.setResultAuditJson(objectMapper.writeValueAsString(audits));
        } catch (Exception e) {
            throw new IllegalStateException("保存失败，请重试");
        }
        recordRepo.save(record);
    }

    @Transactional(readOnly = true)
    public List<ProxyTargetVO> listProxyTargets(Long meetingId, String keyword) {
        CommitteeMeeting meeting = getMeetingForProxy(meetingId);
        MeetingRecord record = getRecord(meeting.getId());
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());

        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return attendances.stream()
                .filter(a -> {
                    if (kw.isEmpty()) return true;
                    String name = Optional.ofNullable(a.getUserRole().getRealName()).orElse("").toLowerCase();
                    String room = Optional.ofNullable(a.getUserRole().getRoomNumber()).orElse("").toLowerCase();
                    return name.contains(kw) || room.contains(kw);
                })
                .map(a -> ProxyTargetVO.builder()
                        .memberId(a.getUserRole().getId())
                        .name(a.getUserRole().getRealName())
                        .role(a.getUserRole().getRole().name())
                        .roomNumber(a.getUserRole().getRoomNumber())
                        .signedIn(a.getSignedIn())
                        .signed(a.getSigned())
                        .signInByProxy(Boolean.TRUE.equals(a.getIsProxy()))
                        .signInOperatorName(a.getOperator() != null ? a.getOperator().getRealName() : null)
                        .proofUrl(a.getProofUrl())
                        .votedTopicIds(getVotedTopicIds(record, a.getUserRole().getId()))
                        .proxyVotes(getProxyVotes(record, a.getUserRole().getId()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void proxyAction(Long meetingId, ProxyActionRequest req) {
        CommitteeMeeting meeting = getMeetingForProxy(meetingId);
        validateProxyRequest(req);
        UserRoleEntity operator = SecurityUtils.getCurrentUserRole();
        MeetingRecord record = getRecord(meeting.getId());

        if ("signIn".equals(req.getActionType())) {
            proxySignIn(record, req, operator);
        } else if ("vote".equals(req.getActionType())) {
            proxyVote(record, req, operator);
        } else {
            throw new IllegalArgumentException("不支持的代录类型");
        }
    }

    // ===== Flags =====
    @Transactional
    public void toggleFlag(Long meetingId, String flag) {
        MeetingRecord record = getRecord(meetingId);
        if ("hasDecision".equals(flag)) record.setHasDecision(!record.getHasDecision());
        else if ("hasMajorIssue".equals(flag)) {
            record.setHasMajorIssue(!record.getHasMajorIssue());
            if (!record.getHasMajorIssue()) {
                // Remove major topics
                List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
                topics.stream()
                        .filter(t -> t.getType() == TopicType.major)
                        .forEach(topicRepo::delete);
            }
        }
        recordRepo.save(record);
    }

    @Transactional
    public void toggleJuwei(Long meetingId) {
        MeetingRecord record = getRecord(meetingId);
        record.setJuweiSigned(!record.getJuweiSigned());
        recordRepo.save(record);
    }

    // ===== 录音多条：上传存文件，不自动转写 =====
    /** 上传录音只存文件信息到新表，返回录音记录 ID。主任"选片"时再触发 ASR 转写。 */
    @Transactional
    public Long saveRecording(Long meetingId, String url, String fileName, Long fileSize, Integer durationSec) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        UserRoleEntity uploader = SecurityUtils.getCurrentUserRole();
        MeetingRecording recording = MeetingRecording.builder()
                .meeting(meeting)
                .uploader(uploader)
                .recordingUrl(url)
                .fileName(fileName)
                .fileSize(fileSize)
                .durationSec(durationSec)
                .asrStatus("none")
                .build();
        recording = recordingRepo.save(recording);
        return recording.getId();
    }

    /** 删除一条录音（转写页可删废录/多余段）。仅删本会议下的记录，返回删除是否成功。
     *  ASR 缓存的逐条转写结果由调用方(控制器)负责 evict，避免对 AsrService 形成循环依赖。 */
    @Transactional
    public void deleteRecording(Long meetingId, Long recordingId) {
        MeetingRecording r = recordingRepo.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("录音不存在"));
        if (r.getMeeting() == null || !r.getMeeting().getId().equals(meetingId)) {
            throw new IllegalArgumentException("录音不属于该会议");
        }
        recordingRepo.delete(r);
    }

    /** 获取某会议全部录音列表（按创建时间倒序） */
    public List<RecordingVO> getRecordings(Long meetingId) {
        return recordingRepo.findByMeetingIdOrderByCreatedAtDesc(meetingId).stream()
                .map(this::toRecordingVO)
                .collect(Collectors.toList());
    }

    /** 根据 ID 获取录音记录的 URL，供 ASR 服务调用 */
    public String getRecordingUrl(Long recordingId) {
        MeetingRecording r = recordingRepo.findById(recordingId).orElse(null);
        return r != null ? r.getRecordingUrl() : null;
    }

    private RecordingVO toRecordingVO(MeetingRecording r) {
        return RecordingVO.builder()
                .id(r.getId())
                .uploaderName(r.getUploader() != null ? r.getUploader().getRealName() : null)
                .recordingUrl(r.getRecordingUrl())
                .fileName(r.getFileName())
                .fileSize(r.getFileSize())
                .durationSec(r.getDurationSec())
                .asrStatus(r.getAsrStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }

    // ensureSignedIn 保留：MeetingRecord 仍然存在（签到/议题/佐证管理），只是录音移到了 MeetingRecording 表
    private void ensureSignedIn(MeetingRecord record, Long userRoleId) {
        RecordAttendance a = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), userRoleId)
                .orElseThrow(() -> new IllegalArgumentException("不在本次会议名单中"));
        if (!Boolean.TRUE.equals(a.getSignedIn())) {
            throw new IllegalArgumentException("请先确认参会再负责录音");
        }
    }

    // ===== 导出签到名单（CSV，仅姓名为主，不含房号） =====
    public Map<String, Object> exportAttendanceCsv(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        MeetingRecord record = getRecord(meetingId);
        List<RecordAttendance> present = attendanceRepo.findByRecordId(record.getId()).stream()
                .filter(RecordAttendance::getSignedIn)
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("﻿"); // UTF-8 BOM，Excel 直接识别中文
        sb.append("序号,姓名,角色,确认参会时间\n");
        int i = 1;
        for (RecordAttendance a : present) {
            String time = a.getOperatedAt() != null ? a.getOperatedAt().toString().replace('T', ' ') : "";
            sb.append(i++).append(',')
              .append(csv(a.getUserRole().getRealName())).append(',')
              .append(csv(a.getUserRole().getRole().name())).append(',')
              .append(csv(time)).append('\n');
        }
        Map<String, Object> result = new HashMap<>();
        result.put("fileName", (m.getTitle() != null ? m.getTitle() : "会议") + "-签到名单.csv");
        result.put("content", sb.toString());
        result.put("count", present.size());
        return result;
    }

    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    // ===== Evidence =====
    @Transactional
    public void addEvidence(Long meetingId, String fileName, String fileType, String fileUrl) {
        MeetingRecord record = getRecord(meetingId);
        evidenceRepo.save(RecordEvidence.builder()
                .record(record)
                .fileName(fileName)
                .fileType(fileType)
                .fileUrl(fileUrl)
                .build());
    }

    @Transactional
    public void removeEvidence(Long meetingId, Long evidenceId) {
        evidenceRepo.deleteById(evidenceId);
    }

    // ===== Materials（会议材料）=====
    @Transactional
    public void addMaterial(Long meetingId, String fileName, String fileType, String sizeText, String fileUrl) {
        String newKey = materialDuplicateKey(fileName, sizeText);
        boolean exists = materialRepo.findByMeetingId(meetingId).stream()
                .anyMatch(mat -> Objects.equals(newKey, materialDuplicateKey(mat.getFileName(), mat.getSizeText())));
        if (exists) {
            throw new IllegalArgumentException(isImageMaterial(fileName, fileType) ? "这张照片已经上传过了" : "这份文件已经上传过了");
        }
        MeetingMaterial saved = materialRepo.save(MeetingMaterial.builder()
                .meetingId(meetingId)
                .fileName(fileName)
                .fileType(fileType)
                .sizeText(sizeText)
                .fileUrl(fileUrl)
                .build());
        triggerMaterialOcr(saved.getId());
    }

    private String materialDuplicateKey(String fileName, String sizeText) {
        return safeLower(fileName == null ? "" : fileName.trim()) + "::" + safeLower(sizeText == null ? "" : sizeText.trim());
    }

    private boolean isImageMaterial(String fileName, String fileType) {
        String name = safeLower(fileName);
        String type = safeLower(fileType);
        return type.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".bmp")
                || name.endsWith(".gif") || name.endsWith(".heic") || name.endsWith(".heif");
    }

    /**
     * 上传材料后触发 OCR 文字提取（图片/PDF）。等本事务提交后再跑，确保后台线程能查到这条记录；
     * 未启用 OCR（无 DoubaoOcrService Bean）则静默跳过。OCR 仅作纪要补充语料，失败不影响材料本身。
     */
    private void triggerMaterialOcr(Long materialId) {
        DoubaoOcrService ocr = ocrServiceProvider.getIfAvailable();
        if (ocr == null || materialId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { ocr.submitMaterialOcr(materialId); }
            });
        } else {
            ocr.submitMaterialOcr(materialId);
        }
    }

    @Transactional
    public void removeMaterial(Long meetingId, Long materialId) {
        materialRepo.deleteByMeetingIdAndId(meetingId, materialId);
    }

    private List<Map<String, Object>> buildMaterials(Long meetingId) {
        return materialRepo.findByMeetingId(meetingId).stream().map(mat -> {
            retryMaterialOcrIfNeeded(mat);
            Map<String, Object> m = new HashMap<>();
            m.put("id", mat.getId());
            m.put("name", mat.getFileName());
            m.put("sizeText", mat.getSizeText());
            m.put("fileType", mat.getFileType());
            m.put("url", mat.getFileUrl());
            m.put("ocrStatus", mat.getOcrStatus());   // null/processing/done/failed，供前端显示识别状态
            return m;
        }).collect(Collectors.toList());
    }

    private void retryMaterialOcrIfNeeded(MeetingMaterial mat) {
        if (mat == null || mat.getId() == null) return;
        String status = mat.getOcrStatus();
        if ("processing".equals(status) || "done".equals(status)) return;
        if (!isOcrMaterial(mat)) return;
        triggerMaterialOcr(mat.getId());
    }

    private boolean isOcrMaterial(MeetingMaterial mat) {
        String type = safeLower(mat.getFileType());
        String name = safeLower(mat.getFileName());
        String url = safeLower(mat.getFileUrl());
        return hasOcrExt(type) || hasOcrExt(name) || hasOcrExt(url);
    }

    private boolean hasOcrExt(String s) {
        if (s == null || s.isBlank()) return false;
        return s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg")
                || s.endsWith(".webp") || s.endsWith(".bmp") || s.endsWith(".pdf")
                || s.equals("png") || s.equals("jpg") || s.equals("jpeg")
                || s.equals("webp") || s.equals("bmp") || s.equals("pdf")
                || s.contains("image/") || s.contains("application/pdf");
    }

    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    // ===== Archive Extras（补充归档）=====
    @Transactional
    public void addArchiveExtra(Long meetingId, String fileName, String fileType, String sizeText,
                                String reason, String fileUrl, String addedBy) {
        archiveExtraRepo.save(ArchiveExtra.builder()
                .meetingId(meetingId)
                .fileName(fileName)
                .fileType(fileType)
                .sizeText(sizeText)
                .reason(reason)
                .fileUrl(fileUrl)
                .addedBy(addedBy)
                .build());
    }

    private List<Map<String, Object>> buildArchiveExtras(Long meetingId) {
        return archiveExtraRepo.findByMeetingId(meetingId).stream().map(ae -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ae.getId());
            m.put("fileName", ae.getFileName());
            m.put("fileType", ae.getFileType());
            m.put("reason", ae.getReason());
            m.put("addedBy", ae.getAddedBy());
            m.put("sizeText", ae.getSizeText());
            m.put("url", ae.getFileUrl());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 归档材料目录（0723 向真实《业主委员会档案目录》台账看齐）：把一次会议形成的全部归档材料
     * 编号成一份台账（会议记录/纪要/公示 + 会议材料 + 补充材料）。先做后台端点，查看入口后续接。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildArchiveCatalog(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        String mdate = m.getMeetingDate() != null ? m.getMeetingDate().toString() : "";
        List<Map<String, Object>> entries = new ArrayList<>();
        int[] seq = {1};

        MeetingRecord record = recordRepo.findByMeetingId(meetingId).orElse(null);
        if (record != null) {
            boolean hasVote = topicRepo.findByRecordIdOrderBySortOrder(record.getId()).stream()
                    .anyMatch(t -> !voteRepo.findByTopicId(t.getId()).isEmpty());
            entries.add(catalogEntry(seq[0]++, "会议记录", "会议档案", mdate,
                    hasVote ? "会议原始记录，含《会议结果》附页" : "会议原始记录"));
            if (record.getMinutesText() != null && !record.getMinutesText().isBlank())
                entries.add(catalogEntry(seq[0]++, "会议纪要", "会议档案", mdate, "对外公示与存档用"));
        }
        MeetingPublish pub = publishRepo.findByMeetingId(meetingId).orElse(null);
        if (pub != null && Boolean.TRUE.equals(pub.getPublished()))
            entries.add(catalogEntry(seq[0]++, pub.getPublicTitle() != null ? pub.getPublicTitle() : "事项公示材料",
                    "公示公告", pub.getPublishDate() != null ? pub.getPublishDate().toString() : mdate, "已在业委会公示栏公示"));
        for (MeetingMaterial mat : materialRepo.findByMeetingId(meetingId))
            entries.add(catalogEntry(seq[0]++, mat.getFileName(), "会议材料",
                    mat.getCreatedAt() != null ? mat.getCreatedAt().toLocalDate().toString() : mdate,
                    mat.getSizeText() != null ? mat.getSizeText() : ""));
        for (ArchiveExtra ex : archiveExtraRepo.findByMeetingId(meetingId))
            entries.add(catalogEntry(seq[0]++, ex.getFileName(), "补充材料",
                    ex.getCreatedAt() != null ? ex.getCreatedAt().toLocalDate().toString() : mdate,
                    ex.getReason() != null && !ex.getReason().isBlank() ? ex.getReason() : (ex.getSizeText() != null ? ex.getSizeText() : "")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("meetingId", meetingId);
        out.put("meetingTitle", m.getTitle());
        out.put("org", orgFullName(m));
        out.put("meetingDate", mdate);
        out.put("total", entries.size());
        out.put("entries", entries);
        return out;
    }

    private Map<String, Object> catalogEntry(int seq, String name, String category, String date, String note) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("seq", seq);
        e.put("name", name);
        e.put("category", category);
        e.put("date", date);
        e.put("note", note);
        return e;
    }

    // ===== Publish =====
    @Transactional
    public void publish(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        PublishInfoVO info = getPublishInfo(m);
        // TODO: 测试期间暂时跳过三日公示期限校验
        // if (info.getDaysLeft() != null && info.getDaysLeft() < 0) {
        //     throw new IllegalArgumentException("已超过会议结束后三日公示期限，不再补公示");
        // }
        // 无效会议此前未初始化公示记录（仅非无效会议会预建）→ 这里按需创建，保证无效会议也能公示
        MeetingPublish pub = publishRepo.findByMeetingId(meetingId)
                .orElseGet(() -> MeetingPublish.builder().meeting(m).published(false).withdrawn(false).build());
        // 已公示且未撤回：拒绝重复发布（0730 修）——避免连点/多端重复覆盖公示内容与纪要快照
        if (Boolean.TRUE.equals(pub.getPublished())) {
            throw new IllegalArgumentException("本次会议已公示，无需重复发布；如需修改请先撤回公示");
        }
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        pub.setPublished(true);
        pub.setPublishDate(TODAY);
        pub.setPublishedById(ur.getId());
        pub.setPublishedByName(ur.getRealName());
        pub.setPublishedAt(LocalDateTime.now());
        pub.setPublicTitle(buildPublicNoticeTitle(m));
        pub.setPublicContent(buildPublicNoticeContent(m));
        // 重新公示：清除撤回标记
        pub.setWithdrawn(false);
        publishRepo.save(pub);
        // 快照本次公示的纪要版本，使"历史版本"能定位到被公示的内容
        snapshotRevision(meetingId, generateMinutes(meetingId));
    }

    /** 直接归档（0723 补实现，此前前端按钮调的端点不存在）：终局动作——未结束的会置为已结束，
     *  移入资料库并从日常列表隐藏。幂等：已归档再点视为成功。 */
    @Transactional
    public void archive(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (Boolean.TRUE.equals(m.getArchived())) return;
        if (m.getStage() != MeetingStage.ended) m.setStage(MeetingStage.ended);
        m.setArchived(true);
        m.setArchivedAt(LocalDateTime.now());
        meetingRepo.save(m);
    }

    /** 撤销归档（仅误归档用）：已公示的先撤回公示再撤归档；原因必填（记审计日志）。 */
    @Transactional
    public void revokeArchive(Long meetingId, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("请填写撤销原因");
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (!Boolean.TRUE.equals(m.getArchived())) throw new IllegalArgumentException("会议未归档");
        boolean published = publishRepo.findByMeetingId(meetingId)
                .map(p -> Boolean.TRUE.equals(p.getPublished())).orElse(false);
        if (published) throw new IllegalArgumentException("已公示的会议请先撤回公示，再撤销归档");
        m.setArchived(false);
        m.setArchivedAt(null);
        meetingRepo.save(m);
    }

    /** 公示全文（标题+正文，PDF 导出用，0723）：已公示取存档的公示标题/正文，未公示按当前数据现拼。
     *  首行为标题（generateNoticePdf 按首行居中排标题），与公示页所见一致。 */
    @Transactional(readOnly = true)
    public String publicNoticeText(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        MeetingPublish pub = publishRepo.findByMeetingId(meetingId).orElse(null);
        String title = pub != null && pub.getPublicTitle() != null && !pub.getPublicTitle().isBlank()
                ? pub.getPublicTitle() : buildPublicNoticeTitle(m);
        String content = pub != null && pub.getPublicContent() != null && !pub.getPublicContent().isBlank()
                ? pub.getPublicContent() : buildPublicNoticeContent(m);
        return title + "\n\n" + content;
    }

    private String buildPublicNoticeTitle(CommitteeMeeting meeting) {
        MeetingRecord record = recordRepo.findByMeetingId(meeting.getId()).orElse(null);
        List<RecordTopic> topics = record == null ? Collections.emptyList()
                : topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        String subject = topics.size() == 1 ? topics.get(0).getTitle() : meeting.getTitle();
        if (subject == null || subject.isBlank()) subject = "本次会议有关事项";
        subject = subject.replaceAll("^(关于|有关)", "").replaceAll("(的)?(会议|议题)$", "").trim();
        return "关于" + subject + "的公示";
    }

    /** 业委会小区名（无则空串）。 */
    private String communityName(CommitteeMeeting meeting) {
        return meeting.getCommunity() != null && meeting.getCommunity().getName() != null
                ? meeting.getCommunity().getName().trim() : "";
    }

    /**
     * 业委会全称（落款/抬头统一用）：市区前缀+小区名+业主委员会（第X届）。
     * 真实材料（备案证/公章/公告落款）均为此格式，如「上海市黄浦区瞿溪新村业主委员会（第三届）」；
     * 演示户口（全虚构）：「上海市静安区阳光花园业主委员会（第一届）」，见 CommunityProfileSeeder。
     */
    public String orgFullName(CommitteeMeeting meeting) {
        String community = communityName(meeting);
        Community c = meeting.getCommunity();
        String term = c != null && c.getCommitteeTerm() != null && !c.getCommitteeTerm().isBlank()
                ? c.getCommitteeTerm().trim() : "第一届";
        String region = c != null && c.getOrgRegion() != null && !c.getOrgRegion().isBlank()
                ? c.getOrgRegion().trim() : "";
        return region + (community.isBlank() ? "" : community) + "业主委员会（" + term + "）";
    }

    /**
     * 公示正文（0723 向真实公告样张看齐）：开头列依据条款、正文一事一条、
     * 公示期+张榜地点+收意见安排写具体、结尾「特此公示。」、落款带届别+日期。盖章打印后线下加盖。
     */
    private String buildPublicNoticeContent(CommitteeMeeting meeting) {
        String community = communityName(meeting);
        String org = community.isBlank() ? "业主委员会" : community + "业主委员会";
        MeetingRecord record = recordRepo.findByMeetingId(meeting.getId()).orElse(null);
        List<RecordTopic> topics = record == null ? Collections.emptyList()
                : topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        Map<Long, QuickConfirmRequest.TopicResult> confirmed = record == null
                ? Collections.emptyMap() : quickConfirmTopicMap(record);
        StringBuilder text = new StringBuilder();
        text.append("根据《中华人民共和国民法典》《物业管理条例》及本小区《业主大会议事规则》的相关规定，经")
                .append(org).append("会议研究，现将有关事项公示如下：\n\n");
        if (topics.isEmpty()) {
            text.append("本次会议形成的有关事项及会议纪要现予公示。\n");
        } else {
            int index = 1;
            for (RecordTopic topic : topics) {
                text.append(index++).append(". ").append(topic.getTitle());
                QuickConfirmRequest.TopicResult result = confirmed.get(topic.getId());
                if (result != null && result.getResult() != null && !result.getResult().isBlank()) {
                    text.append("：").append(isVoteTopic(topic) ? quickResultLabel(result.getResult()) : result.getResult());
                } else if (topic.getType() == TopicType.notice) {
                    text.append("：有关情况已在会议中通报");
                } else if (topic.getType() == TopicType.discussion) {
                    text.append("：有关意见已在会议中讨论并记录");
                }
                text.append("。\n");
            }
        }
        text.append("\n本公示自").append(TODAY).append("起在本小区业委会公示栏张榜公布，公示期7天。相关会议纪要及附件一并公示。\n");
        text.append("公示期内如有意见或建议，请在业主接待日向业主委员会当面反映，或以书面形式投递至意见箱。\n\n");
        text.append("特此公示。\n\n");
        text.append(org).append("\n").append(TODAY);   // 落款用短名「阳光花园业主委员会」，与纪要/记录口径统一
        return text.toString();
    }

    private static final String[] CN_NUM_SVC = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static String cnNumSvc(int i) { return i >= 1 && i <= 10 ? CN_NUM_SVC[i] : String.valueOf(i); }
    private static String cnDateSvc(LocalDate d) {
        return d == null ? "近期" : d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
    }

    /**
     * 会前公告（0723 补齐"会前7天公告议程征意见"环节）：《业主大会和业主委员会指导规则》第三十九条——
     * 业委会应于会议召开7日前，在物业管理区域内公告会议内容和议程，听取业主意见。面向全体业主，张贴公示栏。
     */
    @Transactional(readOnly = true)
    public String buildPreNoticeContent(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        String community = communityName(m);
        String org = community.isBlank() ? "业主委员会" : community + "业主委员会";
        MeetingRecord record = recordRepo.findByMeetingId(meetingId).orElse(null);
        List<RecordTopic> topics = record == null ? Collections.emptyList()
                : topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        StringBuilder t = new StringBuilder();
        t.append("关于召开业主委员会会议的公告\n\n");
        t.append("根据《中华人民共和国民法典》《物业管理条例》及本小区《业主大会议事规则》的相关规定，")
                .append(org).append("定于").append(cnDateSvc(m.getMeetingDate()))
                .append("召开业主委员会会议，现将会议议程公告如下：\n\n");
        if (topics.isEmpty()) {
            t.append("一、〔会议议题以正式通知为准〕\n");
        } else {
            int i = 1;
            for (RecordTopic topic : topics) t.append(cnNumSvc(i++)).append("、").append(nullToUnknown(topic.getTitle())).append("\n");
        }
        t.append("\n特此公告，请全体业主知悉。\n\n").append(org).append("\n").append(TODAY);   // 落款用短名，与公示/纪要口径统一
        return t.toString();
    }

    /**
     * 撤回公示（见 产品边界定稿.md §5）。必须填写原因并留痕，不丢历史。
     */
    @Transactional
    public void withdrawPublish(Long meetingId, String reason) {
        meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("撤回公示必须填写原因");
        }
        MeetingPublish pub = publishRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("公示记录不存在"));
        if (!Boolean.TRUE.equals(pub.getPublished())) {
            throw new IllegalArgumentException("该纪要尚未公示，无需撤回");
        }
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        pub.setPublished(false);
        pub.setWithdrawn(true);
        pub.setWithdrawnById(ur.getId());
        pub.setWithdrawnByName(ur.getRealName());
        pub.setWithdrawnAt(LocalDateTime.now());
        pub.setWithdrawReason(reason.trim());
        publishRepo.save(pub);
    }

    // ===== Minutes =====
    @Transactional(readOnly = true)
    public String buildQuickMinutesContext(Long meetingId, QuickExtractionVO extraction, AsrResult asr) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        MeetingRecord record = getRecord(meetingId);
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        List<RecordAttendance> present = attendances.stream().filter(RecordAttendance::getSignedIn).toList();
        List<RecordAttendance> absent = attendances.stream().filter(a -> !a.getSignedIn()).toList();
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        Map<Long, QuickConfirmRequest.TopicResult> confirmed = quickConfirmTopicMap(record);

        String host = attendances.stream()
                .filter(a -> a.getUserRole().getRole().isChair())
                .findFirst()
                .map(a -> a.getUserRole().getRealName())
                .orElse(present.isEmpty() ? "未明确说明" : present.get(0).getUserRole().getRealName());

        StringBuilder sb = new StringBuilder();
        sb.append("会议名称：").append(nullToUnknown(m.getTitle())).append('\n');
        sb.append("会议时间：").append(nullToUnknown(m.getMeetingDate())).append(" ").append(nullToUnknown(m.getMeetingTime())).append('\n');
        sb.append("会议地点：").append(nullToUnknown(m.getLocation())).append('\n');
        sb.append("召开方式：").append(m.getMeetingMethod() == com.ywh.enums.MeetingMethod.online ? "线上会议" : "线下会议").append('\n');
        sb.append("会议说明：").append(nullToUnknown(m.getDescription())).append('\n');
        sb.append("主持人：").append(host).append('\n');
        sb.append("应到委员：").append(attendances.size()).append("人\n");
        sb.append("实到委员：").append(present.size()).append("人；名单：")
                .append(present.isEmpty() ? "未明确说明" : present.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、"))).append('\n');
        if (!absent.isEmpty()) {
            sb.append("缺席委员：").append(absent.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、"))).append('\n');
        }
        if (record.getHasMajorIssue()) {
            sb.append("列席/居委会签字：").append(nullToUnknown(record.getJuweiName()))
                    .append(record.getJuweiSigned() ? "（已签字）" : "（未签字）").append('\n');
        }

        sb.append("\n议题及人工确认结果：\n");
        int idx = 1;
        for (RecordTopic topic : topics) {
            QuickConfirmRequest.TopicResult r = confirmed.get(topic.getId());
            sb.append(idx++).append(". 【").append(topicTypeLabel(topic)).append("】").append(topic.getTitle()).append('\n');
            if (r != null) {
                sb.append("   确认结果：").append(nullToUnknown(r.getResult())).append('\n');
                if (r.getSummaryDraft() != null && !r.getSummaryDraft().isBlank()) {
                    sb.append("   已生成议题报告/人工确认内容：\n").append(indent(r.getSummaryDraft(), "   ")).append('\n');
                }
                if (isVoteTopic(topic)) {
                    sb.append("   表决票数：同意 ").append(nullToUnknown(r.getForVotes()))
                            .append("，反对 ").append(nullToUnknown(r.getAgVotes()))
                            .append("，弃权 ").append(nullToUnknown(r.getAbVotes()))
                            .append("，合计 ").append(nullToUnknown(r.getTotalVotes())).append('\n');
                }
                if (r.getSegmentIndexes() != null && !r.getSegmentIndexes().isEmpty()) {
                    sb.append("   关联转写片段序号：").append(r.getSegmentIndexes().stream().map(String::valueOf).collect(Collectors.joining("、"))).append('\n');
                }
            } else {
                sb.append("   确认结果：未明确说明\n");
            }
        }

        sb.append("\n转写全文：\n");
        if (asr != null && asr.getSegments() != null && !asr.getSegments().isEmpty()) {
            int count = 0;
            for (AsrResult.Segment seg : asr.getSegments()) {
                if (count++ >= 160) {
                    sb.append("（后续转写较长，已截断）\n");
                    break;
                }
                sb.append(nullToUnknown(seg.getSpeaker())).append("：").append(nullToUnknown(seg.getText())).append('\n');
            }
        } else {
            sb.append("未明确说明\n");
        }

        sb.append("\n生成要求：请根据以上创建会议内容、人工确认议题结果和转写内容，润色生成正式《业主委员会会议纪要》。");
        sb.append("会议纪要须包含会议基本信息、参会情况、逐项议题内容、讨论/表决情况、决议或结论、后续安排。");
        sb.append("不得编造未出现事实；缺失信息写“未明确说明”；语言正式、客观、中立。");
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String buildQuickMinutesContextCompact(Long meetingId, QuickExtractionVO extraction, AsrResult asr) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        MeetingRecord record = getRecord(meetingId);
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        List<RecordAttendance> present = attendances.stream().filter(RecordAttendance::getSignedIn).toList();
        List<RecordAttendance> absent = attendances.stream().filter(a -> !a.getSignedIn()).toList();
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        Map<Long, QuickConfirmRequest.TopicResult> confirmed = quickConfirmTopicMap(record);

        String host = attendances.stream()
                .filter(a -> a.getUserRole().getRole().isChair())
                .findFirst()
                .map(a -> a.getUserRole().getRealName())
                .orElse(present.isEmpty() ? "未明确说明" : present.get(0).getUserRole().getRealName());

        StringBuilder sb = new StringBuilder();
        sb.append("【会议基本信息】\n");
        String communityName = communityName(m);
        sb.append("小区名称：").append(communityName.isEmpty() ? "未明确说明" : communityName).append('\n');
        sb.append("业委会全称（纪要抬头与落款统一使用此名称）：").append(orgFullName(m)).append('\n');
        sb.append("会议名称：").append(nullToUnknown(m.getTitle())).append('\n');
        sb.append("会议时间：").append(nullToUnknown(m.getMeetingDate())).append(" ").append(nullToUnknown(m.getMeetingTime())).append('\n');
        sb.append("会议地点：").append(nullToUnknown(m.getLocation())).append('\n');
        sb.append("召开方式：").append(m.getMeetingMethod() == com.ywh.enums.MeetingMethod.online ? "线上会议" : "线下会议").append('\n');
        sb.append("会议说明：").append(nullToUnknown(m.getDescription())).append('\n');
        sb.append("主持人：").append(host).append('\n');
        sb.append("应到委员：").append(attendances.size()).append("人\n");
        // 到会构成拆现场/线上（真实纪要句式：「有五名委员现场到会，因公、因病未能现场到会的两名委员在微信工作群同时参加会议」）
        List<RecordAttendance> onsite = present.stream().filter(a -> !"remote".equals(a.getAttendanceMode())).toList();
        List<RecordAttendance> remote = present.stream().filter(a -> "remote".equals(a.getAttendanceMode())).toList();
        sb.append("实到委员：").append(present.size()).append("人；现场到会")
                .append(onsite.size()).append("人（").append(onsite.isEmpty() ? "无" : onsite.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、")))
                .append("），线上参加").append(remote.size()).append("人（").append(remote.isEmpty() ? "无" : remote.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、")))
                .append("）\n");
        if (!absent.isEmpty()) {
            sb.append("缺席委员：").append(absent.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、"))).append('\n');
        }
        if (record.getObserversText() != null && !record.getObserversText().isBlank()) {
            sb.append("列席指导人员（居委/街道/物业等，非委员）：").append(record.getObserversText().trim()).append('\n');
        }
        if (record.getHasMajorIssue()) {
            sb.append("居委会签字：").append(nullToUnknown(record.getJuweiName()))
                    .append(record.getJuweiSigned() ? "（已签字）" : "（未签字）").append('\n');
        }

        sb.append("\n【人工确认后的议题结果】\n");
        int idx = 1;
        for (RecordTopic topic : topics) {
            QuickConfirmRequest.TopicResult r = confirmed.get(topic.getId());
            sb.append(idx++).append(". 【").append(topicTypeLabel(topic)).append("】").append(topic.getTitle()).append('\n');
            // 委员意见：议题审议情况的【主要内容来源】。委员在线提交/语音/认领的意见逐条列出，
            // 纪要的「议题审议」以此为主撰写，录音转写仅作补充。
            List<TopicOpinion> topicOps = opinionRepo.findByTopicId(topic.getId());
            if (!topicOps.isEmpty()) {
                sb.append("   委员意见（议题审议以此为主，逐条源于委员本人表达）：\n");
                for (TopicOpinion op : topicOps) {
                    String sp = op.getUserRole() != null ? op.getUserRole().getRealName()
                            : (op.getSpeakerName() != null && !op.getSpeakerName().isBlank() ? op.getSpeakerName() : "现场发言");
                    sb.append("     - ").append(sp).append("：")
                            .append(compactMinutesInput(op.getContent(), 140)).append('\n');
                }
            }
            if (r == null) {
                sb.append("   记录状态：未明确说明\n");
                continue;
            }
            if (isVoteTopic(topic)) {
                sb.append("   表决确认结果：").append(quickResultLabel(r.getResult())).append('\n');
            } else if (topic.getType() == TopicType.notice) {
                sb.append("   纪要处理口径：通报事项已记录，不按赞成、反对、通过或未通过表述。\n");
            } else {
                sb.append("   纪要处理口径：讨论事项已记录，重点写明主要意见、共识和后续安排，不按表决事项表述。\n");
            }
            if (r.getSummaryDraft() != null && !r.getSummaryDraft().isBlank()) {
                sb.append("   人工确认议题报告摘录：").append(compactMinutesInput(r.getSummaryDraft(), isVoteTopic(topic) ? 260 : 360)).append('\n');
            }
            if (isVoteTopic(topic)) {
                sb.append("   表决票数：同意").append(nullToUnknown(r.getForVotes()))
                        .append("，反对").append(nullToUnknown(r.getAgVotes()))
                        .append("，弃权").append(nullToUnknown(r.getAbVotes()))
                        .append("，合计").append(nullToUnknown(r.getTotalVotes())).append('\n');
            }
        }

        // 会议材料 OCR 摘录：图片/PDF 识别出的文字，让纪要可引用材料中的方案、数据、条款等
        List<MeetingMaterial> materials = materialRepo.findByMeetingId(meetingId);
        List<MeetingMaterial> ocrMaterials = materials.stream()
                .filter(x -> x.getOcrText() != null && !x.getOcrText().isBlank())
                .toList();
        if (!ocrMaterials.isEmpty()) {
            sb.append("\n【会议材料摘录】\n");
            int mi = 1;
            for (MeetingMaterial mt : ocrMaterials) {
                sb.append(mi++).append(". ").append(nullToUnknown(mt.getFileName())).append("：")
                        .append(compactMinutesInput(mt.getOcrText(), 600)).append('\n');
            }
        }

        sb.append("\n【必要转写补充】\n");
        if (asr != null && asr.getSegments() != null && !asr.getSegments().isEmpty()) {
            int count = 0;
            for (AsrResult.Segment seg : asr.getSegments()) {
                if (count++ >= 60) {
                    sb.append("（转写较长，后续已截断；纪要应优先依据人工确认结果。）\n");
                    break;
                }
                sb.append(nullToUnknown(seg.getSpeaker())).append("：").append(nullToUnknown(seg.getText())).append('\n');
            }
        } else {
            sb.append("未明确说明\n");
        }

        sb.append("\n【生成要求】\n");
        sb.append("请严格仿照真实业委会一页式纪要生成正式《业主委员会会议纪要》：第一行只写“小区名称+业委会会议纪要”，正文不用信息块和分节小标题。");
        sb.append("第一自然段交代日期、地点或线上方式、参会情况和主持人；第二自然段用“一是、二是、三是”集中概括议程；第三自然段集中写通报知悉、讨论意见或表决结果，以及已明确的紧接办理动作。");
        sb.append("不逐人展开意见，不写完整投票明细、内部分析、风险、详细待办或没有依据的后续安排；明确出现“原则同意”等保留意见时必须保留原表述。");
        sb.append("表决结果按公文惯例如实写：全票通过写“一致表示同意通过”，有不同意见写“X名委员表示同意，X名委员表示不同意（或弃权）”，未获通过写“经表决，该议题未获通过”，讨论类未形成决议写“经讨论未形成决议”及已明确的下一步。");
        sb.append("会议性质按会议名称如实表述：名称含“联席会议”时写“召开了与××的三方联席会议”，不写成业委会全体委员会议。");
        sb.append("如有列席指导人员，仿照真实纪要在结尾自然段写明「××、××等同志到会指导」；没有则不写。");
        sb.append("优先依据人工确认结果和议题报告摘录，不要逐句复述转写，不要输出冗长背景。");
        sb.append("如有【会议材料摘录】，可据此补充方案要点、数据或条款等事实细节，但人工确认结果与材料冲突时以人工确认结果为准；材料识别可能有误，不确定的不要写入。");
        sb.append("纪要缺失信息直接省略，不写“未明确说明”，不得编造；末尾仅写业委会全称和会议日期，全文以一页为目标。");
        return sb.toString();
    }

    private String quickResultLabel(String result) {
        if ("passed".equals(result)) return "通过";
        if ("rejected".equals(result)) return "未通过";
        if ("abstain".equals(result)) return "弃权";
        if ("unclear".equals(result)) return "未明确说明";
        if ("invalid".equals(result)) return "表决无效";   // 人工改结果（0729）新增词
        if ("notified".equals(result)) return "已通报";
        if ("discussed".equals(result)) return "已讨论";
        return nullToUnknown(result);
    }

    private String compactMinutesInput(String text, int maxLen) {
        if (text == null || text.isBlank()) return "未明确说明";
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLen) return cleaned;
        return cleaned.substring(0, maxLen) + "……";
    }

    public String generateMinutes(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur.getRole().isPropertyMgmt()) {
            throw new IllegalArgumentException("物业不参与小区行政事务，无权查看业委会会议");
        }
        if (isExternal(ur) && !canSeePublished(ur, m)) {
            throw new IllegalArgumentException("会议纪要尚未公示，暂不可查看");
        }
        MeetingRecord record = getRecord(meetingId);
        if (record.getMinutesText() != null && !record.getMinutesText().isBlank()) {
            if (isQuickMinutesOutdated(m, record)) {
                return "【提示】人工确认议题内容已更新，当前纪要可能不是最新版本，请重新生成纪要草稿。\n\n" + record.getMinutesText();
            }
            return record.getMinutesText();
        }
        // 没有正式纪要（AI 生成或人工保存）时返回空字符串。
        // 正式纪要必须由主任"用 AI 生成"或"手写"显式创建后才存在；在此之前不凭空合成
        // 一份"占位纪要"——否则首次进入纪要页就会出现一份来历不明的纪要（公示页同理，
        // 空时应提示"尚未生成"）。结构化预览/归档查看由前端依据会议明细自行渲染。
        return "";
    }

    private String nullToUnknown(Object value) {
        if (value == null) return "未明确说明";
        String s = String.valueOf(value);
        return s.isBlank() ? "未明确说明" : s;
    }

    private String indent(String text, String prefix) {
        if (text == null || text.isBlank()) return "";
        return Arrays.stream(text.split("\\R", -1))
                .map(line -> prefix + line)
                .collect(Collectors.joining("\n"));
    }

    @Transactional
    public void updateMinutes(Long meetingId, String text) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found"));
        MeetingPublish pub = getPublish(m);
        if (Boolean.TRUE.equals(pub.getPublished())) {
            throw new IllegalArgumentException("Published minutes cannot be edited directly. Please withdraw or create a revision.");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Minutes text cannot be empty");
        }
        MeetingRecord record = getRecord(meetingId);
        record.setMinutesText(text);
        record.setMinutesConfirmHash(record.getQuickConfirmHash());
        recordRepo.save(record);
        snapshotRevision(meetingId, text);
    }

    @Transactional
    public void updateQuickAiArtifacts(Long meetingId, String minutesText, String topicReportText, String todoListText) {
        if (minutesText == null || minutesText.trim().isEmpty()) {
            throw new IllegalArgumentException("会议纪要内容不能为空");
        }
        MeetingRecord record = getRecord(meetingId);
        record.setMinutesText(minutesText);
        if (topicReportText != null && !topicReportText.trim().isEmpty()) {
            record.setAiTopicReportText(topicReportText);
        }
        if (todoListText != null && !todoListText.trim().isEmpty()) {
            record.setTodoListText(todoListText);
        }
        record.setMinutesConfirmHash(record.getQuickConfirmHash());
        recordRepo.save(record);
        snapshotRevision(meetingId, minutesText);
    }

    public String getInternalTopicReport(Long meetingId) {
        MeetingRecord record = getRecord(meetingId);
        if (record.getAiTopicReportText() != null && !record.getAiTopicReportText().isBlank()) {
            return record.getAiTopicReportText();
        }
        return "暂无内部AI议题报告，请先在快速会议中生成纪要草稿。";
    }

    public String getTodoListText(Long meetingId) {
        MeetingRecord record = getRecord(meetingId);
        if (record.getTodoListText() != null && !record.getTodoListText().isBlank()) {
            return record.getTodoListText();
        }
        return "无明确待办事项。";
    }

    // ===== 结构化待办（委员可回复进度）=====
    // 固化自 AI 待办文本：第一次进待办页由前端解析后调 initTodos 落库，之后以本表为准，
    // 不再被 AI 文本覆盖。负责人仅文本，不强绑账号；任意委员可更新任意一条，留痕操作人。

    @Transactional(readOnly = true)
    public List<MeetingTodoVO> listTodos(Long meetingId) {
        return todoRepo.findByMeetingIdOrderBySortOrderAsc(meetingId).stream()
                .map(this::toTodoVO).collect(Collectors.toList());
    }

    /** 业委会整体待办（0730 独立待办页）：跨会议聚合全部结构化待办，附来源会议标题/日期。
     *  只返回已固化的待办——尚未走过「确认待办清单」的会议不在此列（固化流程仍在单会议页）。 */
    @Transactional(readOnly = true)
    public List<MeetingTodoVO> listAllTodos() {
        List<MeetingTodo> all = todoRepo.findAll();
        if (all.isEmpty()) return Collections.emptyList();
        Set<Long> ids = all.stream().map(MeetingTodo::getMeetingId).collect(Collectors.toSet());
        Map<Long, CommitteeMeeting> meetings = meetingRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(CommitteeMeeting::getId, m -> m));
        // 新会议在前，同一场内保持固化顺序
        all.sort(Comparator.comparing(MeetingTodo::getMeetingId, Comparator.reverseOrder())
                .thenComparing(MeetingTodo::getSortOrder));
        List<MeetingTodoVO> out = new ArrayList<>(all.size());
        for (MeetingTodo t : all) {
            CommitteeMeeting m = meetings.get(t.getMeetingId());
            // 会议已删的残留待办不返回：它点进去是空会议页，也不该计入「待办 N 项」（0731 用户反馈）
            if (m == null) {
                continue;
            }
            MeetingTodoVO vo = toTodoVO(t);
            vo.setMeetingId(t.getMeetingId());
            vo.setMeetingTitle(m.getTitle());
            vo.setMeetingDate(m.getMeetingDate() == null ? null : m.getMeetingDate().toString());
            out.add(vo);
        }
        return out;
    }

    /** 固化待办。幂等：已固化则忽略本次提交、直接返回现有，避免覆盖委员已更新的状态。 */
    @Transactional
    public List<MeetingTodoVO> initTodos(Long meetingId, List<MeetingTodoVO> items) {
        if (todoRepo.countByMeetingId(meetingId) > 0) {
            return listTodos(meetingId);
        }
        meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        LocalDateTime now = LocalDateTime.now();
        // 来源议题解析用（0724）：把待办的「来源议题」文本匹配到本会议的议题，落 sourceTopicId 追溯
        List<RecordTopic> topics = recordRepo.findByMeetingId(meetingId)
                .map(rec -> topicRepo.findByRecordIdOrderBySortOrder(rec.getId()))
                .orElse(Collections.emptyList());
        List<MeetingTodo> saved = new ArrayList<>();
        int order = 0;
        if (items != null) {
            for (MeetingTodoVO it : items) {
                String title = it.getTitle() == null ? "" : it.getTitle().trim();
                if (title.isEmpty()) continue;
                String sourceRef = blankToNull(it.getSourceRef());
                Long sourceTopicId = resolveSourceTopicId(sourceRef, topics);
                MeetingTodo t = MeetingTodo.builder()
                        .meetingId(meetingId)
                        .title(clip(title, 500))
                        .owner(clip(blankToNull(it.getOwner()), 100))
                        .dueText(clip(blankToNull(it.getDueText()), 100))
                        .status(normalizeTodoStatus(it.getStatus()))
                        .sourceRef(clip(sourceRef, 300))
                        .sourceTopicId(sourceTopicId)
                        .sortOrder(order++)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                saved.add(todoRepo.save(t));
            }
        }
        return saved.stream().map(this::toTodoVO).collect(Collectors.toList());
    }

    /** 主任手动新增一条待办（0729：AI 待办边界难界定，除识别外还需人工增补）。追加到现有清单末尾。 */
    @Transactional
    public MeetingTodoVO addTodo(Long meetingId, MeetingTodoVO item) {
        meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        String title = item == null || item.getTitle() == null ? "" : item.getTitle().trim();
        if (title.isEmpty()) throw new IllegalArgumentException("待办内容不能为空");
        LocalDateTime now = LocalDateTime.now();
        MeetingTodo t = MeetingTodo.builder()
                .meetingId(meetingId)
                .title(clip(title, 500))
                .owner(clip(blankToNull(item.getOwner()), 100))
                .dueText(clip(blankToNull(item.getDueText()), 100))
                .status(normalizeTodoStatus(item.getStatus()))
                .sortOrder((int) todoRepo.countByMeetingId(meetingId)) // 追加到末尾
                .createdAt(now)
                .updatedAt(now)
                .build();
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur != null) { t.setLastActorId(ur.getId()); t.setLastActorName(ur.getRealName()); }
        return toTodoVO(todoRepo.save(t));
    }

    /** 主任修改一条待办的内容/负责人（0729：写错只能删了重加太糙）。已发工单的不允许改，避免与工单内容脱节。 */
    @Transactional
    public MeetingTodoVO updateTodo(Long meetingId, Long todoId, MeetingTodoVO item) {
        MeetingTodo t = todoRepo.findById(todoId)
                .orElseThrow(() -> new IllegalArgumentException("待办不存在"));
        if (!t.getMeetingId().equals(meetingId)) {
            throw new IllegalArgumentException("待办与会议不匹配");
        }
        if (t.getExternalTicketNo() != null || t.getTicketNo() != null) {
            throw new IllegalArgumentException("该待办已创建工单，不能修改内容");
        }
        String title = item == null || item.getTitle() == null ? "" : item.getTitle().trim();
        if (title.isEmpty()) throw new IllegalArgumentException("待办内容不能为空");
        t.setTitle(clip(title, 500));
        t.setOwner(clip(blankToNull(item.getOwner()), 100));
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur != null) { t.setLastActorId(ur.getId()); t.setLastActorName(ur.getRealName()); }
        t.setUpdatedAt(LocalDateTime.now());
        return toTodoVO(todoRepo.save(t));
    }

    /** 把待办的「来源议题」文本匹配到本会议议题（相等或互相包含），拿不到返回 null。仅追溯用。 */
    private Long resolveSourceTopicId(String sourceRef, List<RecordTopic> topics) {
        if (sourceRef == null || sourceRef.isBlank() || topics == null || topics.isEmpty()) return null;
        String ref = sourceRef.trim();
        for (RecordTopic tp : topics) {
            String tt = tp.getTitle() == null ? "" : tp.getTitle().trim();
            if (!tt.isEmpty() && (tt.equals(ref) || tt.contains(ref) || ref.contains(tt))) return tp.getId();
        }
        return null;
    }

    /** 委员更新某条待办状态，记录操作人与时间。 */
    @Transactional
    public MeetingTodoVO updateTodoStatus(Long meetingId, Long todoId, String status) {
        MeetingTodo t = todoRepo.findById(todoId)
                .orElseThrow(() -> new IllegalArgumentException("待办不存在"));
        if (!t.getMeetingId().equals(meetingId)) {
            throw new IllegalArgumentException("待办与会议不匹配");
        }
        t.setStatus(normalizeTodoStatus(status));
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur != null) {
            t.setLastActorId(ur.getId());
            t.setLastActorName(ur.getRealName());
        }
        t.setUpdatedAt(LocalDateTime.now());
        return toTodoVO(todoRepo.save(t));
    }

    /** 主任删除 AI 误识别的待办。已推送为外部工单的事项不能只删本地记录，避免产生失联工单。 */
    @Transactional
    public void deleteTodo(Long meetingId, Long todoId) {
        MeetingTodo t = todoRepo.findById(todoId)
                .orElseThrow(() -> new IllegalArgumentException("待办不存在"));
        if (!t.getMeetingId().equals(meetingId)) {
            throw new IllegalArgumentException("待办与会议不匹配");
        }
        if (t.getExternalTicketNo() != null || t.getTicketNo() != null) {
            throw new IllegalArgumentException("该待办已创建工单，不能直接删除");
        }
        todoRepo.delete(t);
    }

    private MeetingTodoVO toTodoVO(MeetingTodo t) {
        MeetingTodoVO vo = new MeetingTodoVO();
        vo.setId(t.getId());
        vo.setTitle(t.getTitle());
        vo.setOwner(t.getOwner());
        vo.setDueText(t.getDueText());
        vo.setStatus(t.getStatus());
        vo.setLastActorName(t.getLastActorName());
        vo.setUpdatedAt(t.getUpdatedAt() == null ? null
                : t.getUpdatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        vo.setExternalTicketNo(t.getExternalTicketNo());
        vo.setTicketNo(t.getTicketNo());
        vo.setTicketPushedAt(t.getTicketPushedAt() == null ? null
                : t.getTicketPushedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        return vo;
    }

    private static String normalizeTodoStatus(String s) {
        if (s == null) return "todo";
        String v = s.trim().toLowerCase();
        if (v.equals("todo") || v.equals("doing") || v.equals("done")) return v;
        if (v.contains("完成") || v.contains("已办") || v.contains("办结")) return "done";
        if (v.contains("进行") || v.contains("处理中") || v.contains("在做")) return "doing";
        return "todo";
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private boolean isQuickMinutesOutdated(CommitteeMeeting meeting, MeetingRecord record) {
        return meeting != null
                && meeting.getMeetingMode() == MeetingMode.quick
                && record != null
                && record.getQuickConfirmHash() != null
                && !record.getQuickConfirmHash().equals(record.getMinutesConfirmHash());
    }

    private String sha256(String text) {
        if (text == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("计算快速会议确认版本失败");
        }
    }

    /** 追加一条纪要修订快照，版本号自增；内容与上一版相同则跳过。见 §5 */
    private void snapshotRevision(Long meetingId, String content) {
        MinutesRevision last = minutesRevisionRepo
                .findFirstByMeetingIdOrderByVersionNoDesc(meetingId).orElse(null);
        if (last != null && Objects.equals(last.getContent(), content)) {
            return;
        }
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        minutesRevisionRepo.save(MinutesRevision.builder()
                .meetingId(meetingId)
                .versionNo(last == null ? 1 : last.getVersionNo() + 1)
                .content(content)
                .editorId(ur != null ? ur.getId() : null)
                .editorName(ur != null ? ur.getRealName() : null)
                .build());
    }

    /** 纪要修订版本历史，仅治理角色可见。见 §5 */
    @Transactional(readOnly = true)
    public List<MinutesRevisionVO> listMinutesRevisions(Long meetingId) {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (isExternal(ur)) {
            throw new IllegalArgumentException("无权查看纪要修订历史");
        }
        meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        return minutesRevisionRepo.findByMeetingIdOrderByVersionNoDesc(meetingId).stream()
                .map(r -> {
                    MinutesRevisionVO vo = new MinutesRevisionVO();
                    vo.setVersionNo(r.getVersionNo());
                    vo.setEditorName(r.getEditorName());
                    vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                    vo.setContent(r.getContent());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    // ===== Stats =====
    public Map<String, Object> getStats() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        LocalDate today = TODAY;
        int year = today.getYear();
        int month = today.getMonthValue();
        int bimonth = (month + 1) / 2;
        int periodStart = (bimonth - 1) * 2 + 1;
        int periodEnd = bimonth * 2;

        long annualCount = meetingRepo.countValidMeetingsInYear(communityId, year);
        long periodCount = meetingRepo.countValidMeetingsInPeriod(communityId,
                LocalDate.of(year, periodStart, 1),
                LocalDate.of(year, periodEnd, 28));

        long preparing = meetingRepo.findByCommunityIdAndStageOrderByCreatedAtDesc(communityId, MeetingStage.preparing).size();
        long ongoing = meetingRepo.findByCommunityIdAndStageOrderByCreatedAtDesc(communityId, MeetingStage.ongoing).size();
        long ended = meetingRepo.findByCommunityIdAndStageAndComplianceNotOrderByCreatedAtDesc(communityId, MeetingStage.ended, ComplianceStatus.invalid).size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("annualCount", annualCount);
        stats.put("annualTarget", 6);
        stats.put("periodCount", periodCount);
        stats.put("periodTarget", 1);
        stats.put("preparing", preparing);
        stats.put("ongoing", ongoing);
        stats.put("ended", ended);
        stats.put("periodLabel", periodStart + "-" + periodEnd + "月");
        stats.put("annualYear", year);
        return stats;
    }

    public PublishScoreVO getPublishScore() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<CommitteeMeeting> applicable = meetingRepo.findByCommunityIdAndStageAndComplianceNotOrderByCreatedAtDesc(
                communityId, MeetingStage.ended, ComplianceStatus.invalid);

        int ontime = 0, overdue = 0, pending = 0;
        for (CommitteeMeeting m : applicable) {
            PublishInfoVO info = getPublishInfo(m);
            if ("ontime".equals(info.getScoreState())) ontime++;
            else if ("overdue".equals(info.getScoreState()) || "late".equals(info.getScoreState())) overdue++;
            else pending++;
        }

        return PublishScoreVO.builder()
                .total(applicable.size())
                .ontime(ontime)
                .overdue(overdue)
                .pending(pending)
                .build();
    }

    // ===== Remove =====
    @Transactional
    public void removeMeeting(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        // 简化阶段：放开删除限制，任意阶段的会议都可由主任删除（级联清理覆盖全部关联数据）。
        // 便于清理测试数据；正式上线如需治理可再收紧（如禁止删除已公示会议）。
        // 先清理录音文件，避免外键约束报错
        recordingRepo.deleteAll(recordingRepo.findByMeetingIdOrderByCreatedAtDesc(meetingId));
        deliveryRepo.deleteByMeetingId(meetingId);
        // 通知历史（新增表，外键指向会议）——不先清理会触发外键约束导致删除失败
        notificationLogRepo.deleteAll(notificationLogRepo.findByMeetingIdOrderBySentAtAsc(meetingId));
        minutesRevisionRepo.deleteAll(minutesRevisionRepo.findByMeetingIdOrderByVersionNoDesc(meetingId));
        publishRepo.findByMeetingId(meetingId).ifPresent(publishRepo::delete);
        recordRepo.findByMeetingId(meetingId).ifPresent(record -> {
            List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
            // 议题意见外键指向议题，先于议题清理
            opinionRepo.deleteAll(opinionRepo.findByTopicRecordIdOrderByCreatedAtAsc(record.getId()));
            topics.forEach(topic -> voteRepo.deleteAll(voteRepo.findByTopicId(topic.getId())));
            topicRepo.deleteAll(topics);
            attendanceRepo.deleteAll(attendanceRepo.findByRecordId(record.getId()));
            evidenceRepo.deleteAll(evidenceRepo.findByRecordId(record.getId()));
            recordRepo.delete(record);
        });
        meetingRepo.delete(m);
    }

    // ================= Private Helpers =================

    private void applyGeneratedNoticeDraft(CommitteeMeeting m) {
        NoticeDraftVO draft = generateNoticeDraft(m);
        m.setNoticeTitle(draft.getTitle());
        m.setNoticeContent(draft.getContent());
        m.setNoticeStatus("draft");
    }

    private NoticeDraftVO buildNoticeDraft(CommitteeMeeting m) {
        if (m.getNoticeTitle() != null && !m.getNoticeTitle().isBlank()
                && m.getNoticeContent() != null && !m.getNoticeContent().isBlank()) {
            NoticeDraftVO vo = new NoticeDraftVO();
            vo.setTitle(m.getNoticeTitle());
            vo.setContent(m.getNoticeContent());
            vo.setStatus(m.getNoticeStatus() != null ? m.getNoticeStatus() : "draft");
            return vo;
        }
        return generateNoticeDraft(m);
    }

    private NoticeDraftVO generateNoticeDraft(CommitteeMeeting m) {
        NoticeDraftVO vo = new NoticeDraftVO();
        // 正文语气与前端一致：以「新会议通知：{会议名}」开头（原「关于召开…的通知」偏弱，已统一）
        String meetingName = (m.getTitle() != null && !m.getTitle().isBlank()) ? m.getTitle() : "业主委员会会议";
        String title = "新会议通知：" + meetingName;
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.add("");
        if (m.getMeetingMethod() == com.ywh.enums.MeetingMethod.online) {
            lines.add("【线上会议】本次会议以线上方式召开，请留意参会平台。");
        }
        lines.add("会议时间：" + (m.getMeetingDate() != null ? m.getMeetingDate() : "待定")
                + " " + (m.getMeetingTime() != null ? m.getMeetingTime() : ""));
        lines.add("召开方式：" + (m.getMeetingMethod() == com.ywh.enums.MeetingMethod.online ? "线上会议" : "线下会议"));
        lines.add((m.getMeetingMethod() == com.ywh.enums.MeetingMethod.online ? "线上平台：" : "会议地点：")
                + (m.getLocation() != null ? m.getLocation() : "待定"));
        // 会议议题：按准备会议时添加的议题标题，逐条编号列出（1.xxx 换行 2.xxx）
        String topicsText = buildNoticeTopicsText(m);
        if (!topicsText.isBlank()) {
            lines.add("会议议题：");
            lines.add(topicsText);
        } else if (m.getDescription() != null && !m.getDescription().isBlank()) {
            lines.add("会议议题：" + m.getDescription());
        } else {
            lines.add("会议议题：待补充");
        }
        lines.add("");
        lines.add("请各位委员按时参加，并提前查阅会议材料。");
        vo.setTitle(title);
        vo.setContent(String.join("\n", lines));
        vo.setStatus("draft");
        return vo;
    }

    /** 按准备会议时添加的议题标题逐条编号，生成"1.xxx\n2.xxx"文本；无议题返回空串。 */
    private String buildNoticeTopicsText(CommitteeMeeting m) {
        MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
        if (record == null) return "";
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (RecordTopic t : topics) {
            String tt = t.getTitle();
            if (tt == null || tt.isBlank()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(idx).append(".").append(tt.trim());
            idx++;
        }
        return sb.toString();
    }

    private List<UserRoleEntity> findCommitteeMembers(Long communityId) {
        return userRoleRepo.findByCommunityIdAndRoleIn(communityId, List.of("主任", "副主任", "委员"));
    }

    private List<UserRoleEntity> resolveMeetingMembers(Long communityId, List<Long> memberIds) {
        List<UserRoleEntity> members = findCommitteeMembers(communityId);
        if (memberIds == null) {
            if (members.isEmpty()) {
                throw new IllegalArgumentException("未找到可通知的业委会成员");
            }
            return members;
        }
        if (memberIds.isEmpty()) {
            throw new IllegalArgumentException("请选择通知对象");
        }
        Set<Long> selectedIds = new LinkedHashSet<>(memberIds);
        List<UserRoleEntity> selected = members.stream()
                .filter(member -> selectedIds.contains(member.getId()))
                .collect(Collectors.toList());
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("请选择有效的通知对象");
        }
        return selected;
    }

    private MemberSummaryVO toMemberSummary(UserRoleEntity member) {
        MemberSummaryVO ms = new MemberSummaryVO();
        ms.setUserRoleId(member.getId());
        ms.setName(member.getRealName());
        ms.setRole(member.getRole().name());
        ms.setRoomNumber(member.getRoomNumber());
        return ms;
    }

    private MeetingRecord initRecord(CommitteeMeeting m) {
        MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
        if (record == null) {
            record = MeetingRecord.builder()
                    .meeting(m)
                    .hasDecision(true)
                    .hasMajorIssue(false)
                    .juweiName("王红梅（社区居委会）")
                    .juweiSigned(false)
                    .build();
            record = recordRepo.save(record);
        }

        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
        List<Long> existingUserRoleIds = attendanceRepo.findByRecordId(record.getId()).stream()
                .map(a -> a.getUserRole().getId())
                .toList();
        for (MeetingDelivery d : deliveries) {
            if (existingUserRoleIds.contains(d.getUserRole().getId())) {
                continue;
            }
            attendanceRepo.save(RecordAttendance.builder()
                    .record(record)
                    .userRole(d.getUserRole())
                    .signedIn(false)
                    .signed(false)
                    .build());
        }
        return record;
    }

    private List<CreateMeetingRequest.TopicRequest> normalizeCreateTopics(List<CreateMeetingRequest.TopicRequest> rawTopics) {
        List<CreateMeetingRequest.TopicRequest> topics = Optional.ofNullable(rawTopics).orElse(Collections.emptyList()).stream()
                .filter(t -> t != null && t.getTitle() != null && !t.getTitle().trim().isEmpty())
                .collect(Collectors.toList());
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("请至少添加一个会议议题；快速模式需要围绕预设议题进行录音识别");
        }
        for (CreateMeetingRequest.TopicRequest topic : topics) {
            TopicType agendaType = parseAgendaType(topic.getType());
            String decisionType = topic.getDecisionType() != null && !topic.getDecisionType().isBlank()
                    ? topic.getDecisionType()
                    : "simple";
            if (agendaType == TopicType.notice || agendaType == TopicType.discussion) {
                decisionType = "none";
                topic.setOptions(null);
            }
            topic.setDecisionType(decisionType);
            if ("multi_choice".equals(decisionType)) {
                List<Map<String, Object>> options = Optional.ofNullable(topic.getOptions()).orElse(Collections.emptyList()).stream()
                        .filter(o -> o != null && o.get("label") != null && !String.valueOf(o.get("label")).trim().isEmpty())
                        .collect(Collectors.toList());
                if (options.size() < 2) {
                    throw new IllegalArgumentException("多选一议题至少需要两个有效选项：" + topic.getTitle());
                }
                topic.setOptions(options);
            }
        }
        return topics;
    }

    private void savePresetTopics(MeetingRecord record, List<CreateMeetingRequest.TopicRequest> topics) {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        int sort = 1;
        for (CreateMeetingRequest.TopicRequest reqTopic : topics) {
            TopicType type = parseAgendaType(reqTopic.getType());
            RecordTopic topic = RecordTopic.builder()
                    .record(record)
                    .title(reqTopic.getTitle().trim())
                    .type(type)
                    .decisionType(reqTopic.getDecisionType())
                    .optionsJson(toOptionsJson(reqTopic.getOptions()))
                    .sortOrder(sort++)
                    .source("preset")
                    .createdById(ur != null ? ur.getId() : null)
                    .createdByName(ur != null ? ur.getRealName() : null)
                    .realNameVote(Boolean.TRUE.equals(reqTopic.getRealNameVote()))
                    .content(type == TopicType.notice ? reqTopic.getContent() : null)
                    .notified(false)
                    .build();
            topicRepo.save(topic);
        }
    }

    private String toOptionsJson(List<Map<String, Object>> options) {
        if (options == null || options.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new IllegalArgumentException("议题选项格式错误");
        }
    }

    private TopicType parseAgendaType(String type) {
        if ("notice".equals(type)) return TopicType.notice;
        if ("discussion".equals(type)) return TopicType.discussion;
        if ("major".equals(type)) return TopicType.major;
        return TopicType.decision;
    }

    private boolean isVoteTopic(RecordTopic topic) {
        return topic != null && topic.getType() != TopicType.notice && topic.getType() != TopicType.discussion;
    }

    private String topicTypeLabel(RecordTopic topic) {
        if (topic == null || topic.getType() == null) return "议题";
        if (topic.getType() == TopicType.notice) return "通报事项";
        if (topic.getType() == TopicType.discussion) return "讨论事项";
        if (topic.getType() == TopicType.major) return "重大表决";
        return "表决事项";
    }

    private void createRecreation(CommitteeMeeting m) {
        CommitteeMeeting recreation = CommitteeMeeting.builder()
                .community(m.getCommunity())
                .title(m.getTitle().replaceAll("（重新召开）$", "") + "（重新召开）")
                .meetingTime(m.getMeetingTime())
                .location(m.getLocation())
                .meetingMethod(m.getMeetingMethod())
                .stage(MeetingStage.preparing)
                .description(m.getDescription())
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();
        recreation = meetingRepo.save(recreation);

        // Init deliveries
        List<UserRoleEntity> committeeMembers = deliveryRepo.findByMeetingId(m.getId()).stream()
                .map(MeetingDelivery::getUserRole)
                .collect(Collectors.toList());
        if (committeeMembers.isEmpty()) {
            committeeMembers = findCommitteeMembers(m.getCommunity().getId());
        }
        for (UserRoleEntity member : committeeMembers) {
            deliveryRepo.save(MeetingDelivery.builder()
                    .meeting(recreation)
                    .userRole(member)
                    .noticeDelivered(false)
                    .materialDelivered(false)
                    .build());
        }
    }

    private MeetingRecord getRecord(Long meetingId) {
        return recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议记录不存在"));
    }

    private CommitteeMeeting getMeetingForProxy(Long meetingId) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        UserRoleEntity operator = SecurityUtils.getCurrentUserRole();
        if (operator == null || !operator.getRole().isCommitteeOperator()) {
            throw new IllegalArgumentException("仅主任/副主任可代录");
        }
        if (meeting.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅会议进行中可代录");
        }
        return meeting;
    }

    private void validateProxyRequest(ProxyActionRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("代录请求不能为空");
        }
        if (req.getMemberIds() == null || req.getMemberIds().isEmpty()) {
            throw new IllegalArgumentException("请选择代录对象");
        }
        // 代录凭证测试期选填（0722 用户定，上线前再定是否恢复必填——见 docs/上线前TODO.md）
        if ("vote".equals(req.getActionType())) {
            if (req.getTopicId() == null) {
                throw new IllegalArgumentException("请选择投票议题");
            }
            if ((req.getChoice() == null || req.getChoice().isBlank()) && req.getSelectedId() == null) {
                throw new IllegalArgumentException("请选择投票选项");
            }
        }
    }

    private List<Long> getVotedTopicIds(MeetingRecord record, Long userRoleId) {
        return topicRepo.findByRecordIdOrderBySortOrder(record.getId()).stream()
                .filter(topic -> voteRepo.findByTopicIdAndUserRoleId(topic.getId(), userRoleId).isPresent())
                .map(RecordTopic::getId)
                .collect(Collectors.toList());
    }

    /** 该委员"由主任代投"的票：topicId → 当前票值（simple=choice 名；multi=选项 id）。供代投面板"改投"用。
     *  仅 isProxy 的票在此——本人自投的不列入，避免被代投悄悄改掉。 */
    private Map<Long, String> getProxyVotes(MeetingRecord record, Long userRoleId) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (RecordTopic topic : topicRepo.findByRecordIdOrderBySortOrder(record.getId())) {
            voteRepo.findByTopicIdAndUserRoleId(topic.getId(), userRoleId).ifPresent(v -> {
                if (Boolean.TRUE.equals(v.getIsProxy())) {
                    String raw = v.getSelectedId() != null ? String.valueOf(v.getSelectedId())
                            : (v.getChoice() != null ? v.getChoice().name() : null);
                    if (raw != null) map.put(topic.getId(), raw);
                }
            });
        }
        return map;
    }

    private void proxySignIn(MeetingRecord record, ProxyActionRequest req, UserRoleEntity operator) {
        LocalDateTime now = LocalDateTime.now();
        List<String> duplicated = new ArrayList<>();
        List<RecordAttendance> toSave = new ArrayList<>();

        for (Long memberId : new LinkedHashSet<>(req.getMemberIds())) {
            RecordAttendance attendance = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), memberId)
                    .orElseThrow(() -> new IllegalArgumentException("代录对象不在本次会议名单中"));
            if (Boolean.TRUE.equals(attendance.getSignedIn())) {
                duplicated.add(attendance.getUserRole().getRealName());
                continue;
            }
            attendance.setSignedIn(true);
            attendance.setOperator(operator);
            attendance.setIsProxy(true);
            attendance.setProofUrl(req.getProofUrl());
            attendance.setOperatedAt(now);
            toSave.add(attendance);
        }

        if (!duplicated.isEmpty()) {
            throw new IllegalArgumentException("以下成员已确认参会，不能重复代录：" + String.join("、", duplicated));
        }
        attendanceRepo.saveAll(toSave);
    }

    private void proxyVote(MeetingRecord record, ProxyActionRequest req, UserRoleEntity operator) {
        RecordTopic topic = topicRepo.findById(req.getTopicId())
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        if (!topic.getRecord().getId().equals(record.getId())) {
            throw new IllegalArgumentException("议题不属于本次会议");
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> invalid = new ArrayList<>();
        List<TopicVote> toSave = new ArrayList<>();

        for (Long memberId : new LinkedHashSet<>(req.getMemberIds())) {
            RecordAttendance attendance = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), memberId)
                    .orElseThrow(() -> new IllegalArgumentException("代录对象不在本次会议名单中"));
            if (!Boolean.TRUE.equals(attendance.getSignedIn())) {
                invalid.add(attendance.getUserRole().getRealName() + "未确认参会");
                continue;
            }
            // 已有投票：之前"代投"的允许覆盖更新（主任改正代投错的票）；委员本人自投的不许被代改
            TopicVote existing = voteRepo.findByTopicIdAndUserRoleId(topic.getId(), memberId).orElse(null);
            if (existing != null && !Boolean.TRUE.equals(existing.getIsProxy())) {
                invalid.add(attendance.getUserRole().getRealName() + "本人已投票，不能代改");
                continue;
            }
            TopicVote vote = (existing != null) ? existing : TopicVote.builder()
                    .topic(topic)
                    .userRole(attendance.getUserRole())
                    .build();
            vote.setOperator(operator);
            vote.setIsProxy(true);
            vote.setProofUrl(req.getProofUrl());
            vote.setOperatedAt(now);
            // 改投时先清另一种存储，避免 simple/multi 切换后残留旧值
            vote.setChoice(null);
            vote.setSelectedId(null);
            if (req.getSelectedId() != null) {
                vote.setSelectedId(req.getSelectedId());
            } else {
                vote.setChoice(VoteChoice.valueOf(req.getChoice()));
            }
            toSave.add(vote);
        }

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("以下成员不能代投票：" + String.join("、", invalid));
        }
        voteRepo.saveAll(toSave);
    }

    private MeetingPublish getPublish(CommitteeMeeting m) {
        return publishRepo.findByMeetingId(m.getId())
                .orElse(MeetingPublish.builder().meeting(m).published(false).withdrawn(false).build());
    }

    /** 准备阶段：被通知的委员是否都已回复（确认参会 或 缺席）。供首页主按钮显示"会议已就绪"。
     *  需通知已全部送达，且每位被通知人都有 signedIn 或 declined 的回复。 */
    private boolean isPreparingAllReplied(CommitteeMeeting m) {
        if (m.getStage() != MeetingStage.preparing) return false;
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
        if (deliveries.isEmpty()) return false;
        if (!deliveries.stream().allMatch(d -> Boolean.TRUE.equals(d.getNoticeDelivered()))) return false;
        MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
        if (record == null) return false;
        Map<Long, RecordAttendance> byUser = attendanceRepo.findByRecordId(record.getId()).stream()
                .collect(Collectors.toMap(a -> a.getUserRole().getId(), a -> a, (a, b) -> a));
        for (MeetingDelivery d : deliveries) {
            RecordAttendance a = byUser.get(d.getUserRole().getId());
            boolean replied = a != null
                    && (Boolean.TRUE.equals(a.getSignedIn()) || Boolean.TRUE.equals(a.getDeclined()));
            if (!replied) return false;
        }
        return true;
    }

    private DeliveryInfoVO getDeliveryInfo(CommitteeMeeting m) {
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
        int noticeDone = (int) deliveries.stream().filter(d -> Boolean.TRUE.equals(d.getNoticeDelivered())).count();
        int materialDone = (int) deliveries.stream().filter(d -> Boolean.TRUE.equals(d.getMaterialDelivered())).count();
        int readDone = (int) deliveries.stream().filter(d -> d.getNoticeReadAt() != null).count();
        boolean allDone = !deliveries.isEmpty() && noticeDone == deliveries.size() && materialDone == deliveries.size();

        LocalDate meetingDate = m.getMeetingDate();
        String deadlineStr = "待定";
        Integer daysLeft = null;
        if (meetingDate != null) {
            LocalDate deadline = meetingDate.minusDays(7);
            deadlineStr = deadline.toString();
            daysLeft = (int) ChronoUnit.DAYS.between(TODAY, deadline);
        }

        List<DeliveryInfoVO.MemberDeliveryVO> memberDeliveries = deliveries.stream()
                .map(d -> {
                    DeliveryInfoVO.MemberDeliveryVO md = new DeliveryInfoVO.MemberDeliveryVO();
                    md.setUserRoleId(d.getUserRole().getId());
                    md.setName(d.getUserRole().getRealName());
                    md.setRole(d.getUserRole().getRole().name());
                    md.setNoticeDelivered(d.getNoticeDelivered());
                    md.setMaterialDelivered(d.getMaterialDelivered());
                    md.setNoticeRead(d.getNoticeReadAt() != null);
                    return md;
                }).collect(Collectors.toList());

        DeliveryInfoVO vo = new DeliveryInfoVO();
        vo.setDeadlineStr(deadlineStr);
        vo.setDaysLeft(daysLeft);
        vo.setNoticeDone(noticeDone);
        vo.setMaterialDone(materialDone);
        vo.setReadDone(readDone);
        vo.setTotal(deliveries.size());
        vo.setAllDone(allDone);
        vo.setNoDate(meetingDate == null);
        vo.setMemberDeliveries(memberDeliveries);
        return vo;
    }

    /** 当前用户在准备阶段的个人送达/已读状态；不在送达名单内则返回 null。 */
    private MyDeliveryVO getMyDelivery(CommitteeMeeting m, UserRoleEntity ur) {
        if (m.getStage() != MeetingStage.preparing || ur == null) return null;
        return deliveryRepo.findByMeetingIdAndUserRoleId(m.getId(), ur.getId())
                .map(d -> {
                    MyDeliveryVO vo = new MyDeliveryVO();
                    vo.setNoticeDelivered(d.getNoticeDelivered());
                    vo.setMaterialDelivered(d.getMaterialDelivered());
                    vo.setNoticeRead(d.getNoticeReadAt() != null);
                    vo.setMaterialRead(d.getMaterialReadAt() != null);
                    return vo;
                })
                .orElse(null);
    }

    private RecordInfoVO getRecordInfo(CommitteeMeeting m) {
        MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
        if (record == null) {
            if (m.getStage() == MeetingStage.preparing) {
                RecordInfoVO vo = emptyPreparingRecordInfo();
                // 准备阶段也能看到已上传的录音
                vo.setRecordings(getRecordings(m.getId()));
                return vo;
            }
            throw new IllegalArgumentException("会议记录不存在");
        }
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        List<RecordEvidence> evidences = evidenceRepo.findByRecordId(record.getId());

        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        // 表决分母只算已签到参会者（0722 用户定：请假/未参会不计入表决；已签到者都应有投票结果）
        int total = (int) attendances.stream().filter(a -> Boolean.TRUE.equals(a.getSignedIn())).count();
        // 通报类「已读进度」用：会议参会名单 id 集合 + 人数（作为"全体已通报"的分母）。
        // 请假等例外由主任使用「标记全体已通报」人工收口，不让签到先后顺序提前完成通报。
        Set<Long> attendeeIds = attendances.stream()
                .map(a -> a.getUserRole().getId())
                .collect(Collectors.toSet());
        int attendeeCount = attendeeIds.size();

        List<RecordInfoVO.AttendanceVO> attendanceVOs = attendances.stream().map(a -> {
            RecordInfoVO.AttendanceVO av = new RecordInfoVO.AttendanceVO();
            av.setUserRoleId(a.getUserRole().getId());
            av.setName(a.getUserRole().getRealName());
            av.setRole(a.getUserRole().getRole().name());
            av.setRoomNumber(a.getUserRole().getRoomNumber());
            av.setSignedIn(a.getSignedIn());
            av.setSigned(a.getSigned());
            av.setDeclined(Boolean.TRUE.equals(a.getDeclined()));
            av.setAttendanceMode(a.getAttendanceMode());
            av.setProxySignAuthorized(Boolean.TRUE.equals(a.getProxySignAuthorized()));
            av.setProxySignAuthorizedAt(a.getProxySignAuthorizedAt() == null ? null : a.getProxySignAuthorizedAt().toString());
            av.setIsSelf(ur.getId().equals(a.getUserRole().getId()));
            av.setIsProxy(Boolean.TRUE.equals(a.getIsProxy()));
            av.setOperatorName(a.getOperator() != null ? a.getOperator().getRealName() : null);
            av.setProofUrl(a.getProofUrl());
            return av;
        }).collect(Collectors.toList());

        Map<Long, QuickConfirmRequest.TopicResult> quickConfirmTopics = quickConfirmTopicMap(record);
        Map<Long, String> resultAudits = latestResultAudit(record);
        List<RecordInfoVO.TopicVO> topicVOs = topics.stream().map(tp -> {
            List<TopicVote> votes = voteRepo.findByTopicId(tp.getId());
            int need = total / 2 + 1;
            String decisionType = tp.getDecisionType() != null ? tp.getDecisionType() : "simple";
            List<Map<String, Object>> options = parseTopicOptions(tp);
            int forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
            int agV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.against).count();
            int abV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.abstain).count();
            QuickConfirmRequest.TopicResult quickResult = quickConfirmTopics.get(tp.getId());
            int countedVotes = votes.size();
            if (quickResult != null && quickResult.getForVotes() != null) {
                // 理顺：现场确认的汇总票数与 app 内逐人投票"逐桶取大"合并展示——
                // 现场汇总覆盖没在 app 投票的举手表决者，逐人票保证 app 已投的不被吞掉。
                forV = Math.max(forV, Optional.ofNullable(quickResult.getForVotes()).orElse(0));
                agV = Math.max(agV, Optional.ofNullable(quickResult.getAgVotes()).orElse(0));
                abV = Math.max(abV, Optional.ofNullable(quickResult.getAbVotes()).orElse(0));
                countedVotes = Math.max(votes.size(), forV + agV + abV);
            }
            boolean voteRequired = isVoteTopic(tp);
            boolean passed;
            String statusText;

            if (!voteRequired) {
                passed = true;
                statusText = tp.getType() == TopicType.notice ? "通报记录" : "讨论记录";
            } else if ("multi_choice".equals(decisionType)) {
                Map<Long, Integer> counts = new HashMap<>();
                votes.stream()
                        .filter(v -> v.getSelectedId() != null)
                        .forEach(v -> counts.merge(v.getSelectedId(), 1, Integer::sum));
                if (quickResult != null && quickResult.getOptionVotes() != null) {
                    quickResult.getOptionVotes().forEach((optionId, count) ->
                            counts.merge(optionId, Optional.ofNullable(count).orElse(0), Math::max));
                    countedVotes = Math.max(countedVotes,
                            quickResult.getOptionVotes().values().stream()
                                    .filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
                }
                int leadingVotes = 0;
                Map<String, Object> leadingOption = null;
                for (Map<String, Object> option : options) {
                    Long optionId = toLong(option.get("id"));
                    int optionVotes = optionId == null ? 0 : counts.getOrDefault(optionId, 0);
                    option.put("votes", optionVotes);
                    if (optionVotes > leadingVotes) {
                        leadingVotes = optionVotes;
                        leadingOption = option;
                    }
                }
                passed = leadingVotes >= need;
                statusText = passed
                        ? "决议通过：" + Optional.ofNullable(leadingOption).map(o -> String.valueOf(o.get("label"))).orElse("")
                        : (countedVotes < total ? "待继续表决" : "决议未通过");
            } else {
                passed = forV >= need;
                statusText = getTopicStatusText(tp, total, forV, agV, abV);
            }

            RecordInfoVO.TopicVO tv = new RecordInfoVO.TopicVO();
            tv.setId(tp.getId());
            tv.setTitle(tp.getTitle());
            tv.setType(tp.getType().name());
            tv.setVoteRequired(voteRequired);
            tv.setDecisionType(decisionType);
            tv.setOptions(options);
            tv.setForVotes(forV);
            tv.setAgVotes(agV);
            tv.setAbVotes(abV);
            tv.setVoted(countedVotes);
            tv.setOpinionCount((int) opinionRepo.countByTopicId(tp.getId()));
            tv.setTotal(total);
            tv.setNeed(need);
            tv.setPassed(passed);
            tv.setVoteClosed(Boolean.TRUE.equals(tp.getVoteClosed()));
            tv.setStatus(!voteRequired ? "recorded" : (passed ? "passed" : (countedVotes < total ? "pending" : "failed")));
            tv.setText(statusText);
            tv.setSummaryDraft(quickResult != null ? quickResult.getSummaryDraft() : null);
            // 议题结果人工改动（0729）：改过才带 audit 文案；前端以「有留痕」判定是否用改后结果覆盖显示
            tv.setConfirmedResult(quickResult != null ? quickResult.getResult() : null);
            tv.setResultAuditText(resultAudits.get(tp.getId()));
            // 通报类：正文 + 已通报 + 本人是否看过 + 已读进度（已确认「我已读」人数 / 参会名单人数）
            tv.setContent(tp.getContent());
            tv.setNotified(Boolean.TRUE.equals(tp.getNotified()));
            Set<Long> viewedIds = parseViewedBy(tp.getViewedByJson());
            tv.setViewedByMe(viewedIds.contains(ur.getId()));
            tv.setViewedCount((int) viewedIds.stream().filter(attendeeIds::contains).count());
            // 暂沿用既有 JSON 字段 signedInCount 以保持前端兼容；其业务含义现为参会名单人数。
            tv.setSignedInCount(attendeeCount);

            TopicVote myVote = votes.stream()
                    .filter(v -> v.getUserRole().getId().equals(ur.getId()))
                    .findFirst().orElse(null);
            if (myVote != null) {
                if (myVote.getSelectedId() != null) {
                    tv.setMySelectedId(myVote.getSelectedId());
                    tv.setMyVote(String.valueOf(myVote.getSelectedId()));
                    tv.setMyVoteLabel(findOption(options, myVote.getSelectedId()));
                } else if (myVote.getChoice() != null) {
                    tv.setMyVote(myVote.getChoice().name());
                }
            }

            // 留痕（规则6）
            tv.setSource(tp.getSource());
            tv.setCreatedByName(tp.getCreatedByName());
            tv.setCreatedAt(tp.getCreatedAt() != null ? tp.getCreatedAt().toString() : null);
            // 实名表决（规则5）：仅实名议题公开个人选择，否则不暴露
            boolean realName = Boolean.TRUE.equals(tp.getRealNameVote());
            tv.setRealNameVote(realName);
            if (realName) {
                List<Map<String, Object>> voterChoices = votes.stream().map(v -> {
                    Map<String, Object> vc = new HashMap<>();
                    vc.put("name", v.getUserRole().getRealName());
                    if (v.getSelectedId() != null) {
                        vc.put("choice", String.valueOf(v.getSelectedId()));
                        Map<String, Object> opt = findOption(options, v.getSelectedId());
                        vc.put("label", opt != null ? opt.get("label") : null);
                    } else if (v.getChoice() != null) {
                        vc.put("choice", v.getChoice().name());
                    }
                    vc.put("isProxy", Boolean.TRUE.equals(v.getIsProxy()));
                    return vc;
                }).collect(Collectors.toList());
                tv.setVoterChoices(voterChoices);
            }
            return tv;
        }).collect(Collectors.toList());

        List<RecordInfoVO.EvidenceVO> evidenceVOs = evidences.stream().map(e -> {
            RecordInfoVO.EvidenceVO ev = new RecordInfoVO.EvidenceVO();
            ev.setId(e.getId());
            ev.setFileName(e.getFileName());
            ev.setFileType(e.getFileType());
            ev.setFileUrl(e.getFileUrl());
            return ev;
        }).collect(Collectors.toList());

        // Check results
        int need = total / 2 + 1;
        int signedIn = (int) attendances.stream().filter(RecordAttendance::getSignedIn).count();

        List<RecordInfoVO.CheckVO> checks = new ArrayList<>();
        RecordInfoVO.CheckVO c1 = new RecordInfoVO.CheckVO();
        c1.setLabel("委员确认参会过半");
        c1.setDetail(signedIn + "/" + total + "（需≥" + need + "）");
        c1.setOk(signedIn >= need);
        checks.add(c1);

        if (record.getHasMajorIssue()) {
            RecordInfoVO.CheckVO c4 = new RecordInfoVO.CheckVO();
            c4.setLabel("重大事项·居委会委员签字");
            c4.setDetail(record.getJuweiSigned() ? "已签字" : "待签字");
            c4.setOk(record.getJuweiSigned());
            checks.add(c4);
        }
        RecordInfoVO.CheckVO c5 = new RecordInfoVO.CheckVO();
        c5.setLabel("线下佐证归档");
        c5.setDetail(evidences.isEmpty() ? "待上传" : "已上传 " + evidences.size() + " 份");
        c5.setOk(!evidences.isEmpty());
        checks.add(c5);

        boolean requiredOk = checks.stream().filter(c -> !c.getLabel().equals("线下佐证归档")).allMatch(RecordInfoVO.CheckVO::getOk);
        boolean softOk = !evidences.isEmpty();
        String recordLevel = requiredOk && softOk ? "complete" : requiredOk ? "minor" : "incomplete";
        String recordText = "complete".equals(recordLevel) ? "记录完整"
                : "minor".equals(recordLevel) ? "记录有瑕疵，待补充归档" : "关键记录待补正";

        RecordInfoVO vo = new RecordInfoVO();
        vo.setHasDecision(record.getHasDecision());
        vo.setHasMajorIssue(record.getHasMajorIssue());
        vo.setJuweiName(record.getJuweiName());
        vo.setJuweiSigned(record.getJuweiSigned());
        vo.setRecordingUrl(null);
        vo.setRecordings(getRecordings(m.getId()));
        vo.setAttendances(attendanceVOs);
        vo.setTopics(topicVOs);
        vo.setEvidences(evidenceVOs);
        vo.setChecks(checks);
        vo.setRecordLevel(recordLevel);
        vo.setRecordText(recordText);
        return vo;
    }

    private RecordInfoVO emptyPreparingRecordInfo() {
        RecordInfoVO vo = new RecordInfoVO();
        vo.setHasDecision(false);
        vo.setHasMajorIssue(false);
        vo.setJuweiSigned(false);
        vo.setAttendances(Collections.emptyList());
        vo.setTopics(Collections.emptyList());
        vo.setEvidences(Collections.emptyList());
        vo.setChecks(Collections.emptyList());
        vo.setRecordings(Collections.emptyList());
        vo.setRecordLevel("preparing");
        vo.setRecordText("准备阶段");
        return vo;
    }

    private PublishInfoVO getPublishInfo(CommitteeMeeting m) {
        MeetingPublish pub = getPublish(m);
        LocalDate base = m.getMeetingDate() != null ? m.getMeetingDate() : TODAY;
        LocalDate deadline = base.plusDays(3);
        int daysLeft = (int) ChronoUnit.DAYS.between(TODAY, deadline);

        String scoreState;
        if (pub.getPublished()) {
            boolean onTime = pub.getPublishDate() != null && !pub.getPublishDate().isAfter(deadline);
            scoreState = onTime ? "ontime" : "late";
        } else {
            scoreState = daysLeft < 0 ? "overdue" : "pending";
        }

        PublishInfoVO vo = new PublishInfoVO();
        vo.setPublished(pub.getPublished());
        vo.setPublishDate(pub.getPublishDate() != null ? pub.getPublishDate().toString() : null);
        vo.setPublicTitle(pub.getPublicTitle());
        vo.setPublicContent(pub.getPublicContent());
        vo.setDeadlineStr(deadline.toString());
        vo.setDaysLeft(daysLeft);
        vo.setScoreState(scoreState);
        // 公示状态机
        boolean withdrawn = Boolean.TRUE.equals(pub.getWithdrawn());
        vo.setStatus(Boolean.TRUE.equals(pub.getPublished()) ? "published" : withdrawn ? "withdrawn" : "pending");
        vo.setPublishedBy(pub.getPublishedByName());
        vo.setPublishedAt(pub.getPublishedAt() != null ? pub.getPublishedAt().toString() : null);
        vo.setWithdrawn(withdrawn);
        vo.setWithdrawnBy(pub.getWithdrawnByName());
        vo.setWithdrawnAt(pub.getWithdrawnAt() != null ? pub.getWithdrawnAt().toString() : null);
        vo.setWithdrawReason(pub.getWithdrawReason());
        return vo;
    }

    private List<MemberSummaryVO> getMemberSummaries(CommitteeMeeting m) {
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
        return deliveries.stream()
                .map(d -> toMemberSummary(d.getUserRole()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> parseTopicOptions(RecordTopic topic) {
        if (topic.getOptionsJson() == null || topic.getOptionsJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(topic.getOptionsJson(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> findOption(List<Map<String, Object>> options, Long selectedId) {
        if (selectedId == null) return null;
        return options.stream()
                .filter(option -> selectedId.equals(toLong(option.get("id"))))
                .findFirst()
                .orElse(null);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getTopicStatusText(RecordTopic tp, int total, int forV, int agV, int abV) {
        int need = total / 2 + 1;
        int voted = forV + agV + abV;
        if (forV >= need) return tp.getType() == TopicType.major ? "形成提请意见" : "决议通过";
        if (voted < total) return "待继续表决";
        return "决议未通过";
    }

    private Map<String, Object> evaluateEndResult(CommitteeMeeting m) {
        MeetingRecord record = getRecord(m.getId());
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        int total = attendances.size();
        int need = total / 2 + 1;

        int signedIn = (int) attendances.stream().filter(RecordAttendance::getSignedIn).count();

        Map<String, Object> result = new HashMap<>();
        List<String> notes = new ArrayList<>();

        if (signedIn < need) {
            notes.add("未达法定人数：仅 " + signedIn + "/" + total + " 名委员确认参会（需≥" + need + "），会议不成立");
            result.put("level", "invalid");
            result.put("notes", notes);
            result.put("conclusion", "会议未达法定人数，会议不成立，本次决议不生效。");
            return result;
        }

        if (record.getHasMajorIssue() && !record.getJuweiSigned()) {
            notes.add("重大事项记录待补正：缺居委会委员签字");
        }

        List<RecordEvidence> evidences = evidenceRepo.findByRecordId(record.getId());
        if (evidences.isEmpty()) {
            notes.add("记录待补充：线下佐证归档（待上传）");
        }

        // Resolution check
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        List<RecordTopic> voteTopics = topics.stream().filter(this::isVoteTopic).collect(Collectors.toList());
        boolean quickMode = m.getMeetingMode() == MeetingMode.quick;
        Map<Long, QuickConfirmRequest.TopicResult> quickConfirmTopics = quickMode
                ? quickConfirmTopicMap(record)
                : Collections.emptyMap();
        int requiredVoteCount = m.getMeetingMode() == MeetingMode.quick ? signedIn : total;
        int voteNeed = quickMode ? signedIn / 2 + 1 : need;
        for (RecordTopic tp : voteTopics) {
            QuickConfirmRequest.TopicResult quickResult = quickConfirmTopics.get(tp.getId());
            int forV;
            int voted;
            if (quickMode && quickResult != null) {
                int agV = Optional.ofNullable(quickResult.getAgVotes()).orElse(0);
                int abV = Optional.ofNullable(quickResult.getAbVotes()).orElse(0);
                forV = Optional.ofNullable(quickResult.getForVotes()).orElse(0);
                voted = forV + agV + abV;
            } else {
                List<TopicVote> votes = voteRepo.findByTopicId(tp.getId());
                forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
                voted = votes.size();
            }
            if (forV < voteNeed) {
                if (voted < requiredVoteCount) {
                    notes.add("\"" + tp.getTitle() + "\"尚未完成表决（已投 " + voted + "/" + requiredVoteCount + "，赞成 " + forV + "/" + voteNeed + "）");
                } else {
                    notes.add("\"" + tp.getTitle() + "\"未通过（赞成 " + forV + "/" + voteNeed + "）");
                }
            }
        }

        boolean allComplete = signedIn >= need
                && (!record.getHasMajorIssue() || record.getJuweiSigned())
                && !evidences.isEmpty()
                && voteTopics.stream().allMatch(tp -> {
                    if (quickMode) {
                        QuickConfirmRequest.TopicResult quickResult = quickConfirmTopics.get(tp.getId());
                        if (quickResult == null) return false;
                        Integer forV = quickResult.getForVotes();
                        Integer agV = quickResult.getAgVotes();
                        Integer abV = quickResult.getAbVotes();
                        if (forV == null || agV == null || abV == null) return false;
                        return forV + agV + abV >= requiredVoteCount && forV >= voteNeed;
                    }
                    List<TopicVote> votes = voteRepo.findByTopicId(tp.getId());
                    int forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
                    return forV >= voteNeed;
                });

        if (allComplete) {
            result.put("level", "valid");
            result.put("notes", notes.isEmpty() ? Collections.emptyList() : notes);
            result.put("conclusion", "会议有效，记录完整，决议已通过。");
        } else {
            result.put("level", "flawed");
            result.put("notes", notes);
            result.put("conclusion", "会议有效，但存在需说明的记录或决议事项，相关决议按表决结果生效。");
        }
        return result;
    }

    private String computeConclusionText(Map<String, Object> er) {
        return "会议有效，但存在需说明的记录或决议事项，相关决议按表决结果生效。";
    }

    private String getFlowNodeText(CommitteeMeeting m) {
        if (m.getStage() == MeetingStage.preparing) {
            DeliveryInfoVO info = getDeliveryInfo(m);
            if (Boolean.TRUE.equals(info.getNoDate())) return "准备阶段 · 日期待补";
            if (!Boolean.TRUE.equals(info.getAllDone())) return "准备阶段 · 会前送达中";
            return "准备阶段 · 待开始会议";
        }
        if (m.getStage() == MeetingStage.ongoing) {
            RecordInfoVO info = getRecordInfo(m);
            int total = info.getAttendances().size();
            long signedIn = info.getAttendances().stream().filter(RecordInfoVO.AttendanceVO::getSignedIn).count();
            if (signedIn < total) return "进行中 · 确认参会中";
            return evaluateEndResult(m).get("level").equals("valid") ? "进行中 · 可结束会议" : "进行中 · 记录补正中";
        }
        if (ComplianceStatus.invalid == m.getCompliance()) return "已结束 · 未成立归档";
        PublishInfoVO pi = getPublishInfo(m);
        if ("pending".equals(pi.getScoreState())) return "已结束 · 纪要待公示";
        if ("overdue".equals(pi.getScoreState())) return "已结束 · 逾期未公示";
        return "已结束 · 已公示归档";
    }

    private Map<String, Object> getTaskSummary(CommitteeMeeting m, UserRoleEntity ur) {
        Map<String, Object> summary = new HashMap<>();
        if (isExternal(ur)) {
            summary.put("level", "readonly");
            summary.put("title", "只读状态");
            if (canSeePublished(ur, m)) {
                summary.put("items", List.of("可查看已公示纪要与决定"));
            } else if (Boolean.TRUE.equals(getPublish(m).getWithdrawn())) {
                summary.put("items", List.of("该纪要已撤回公示"));
            } else if (m.getCompliance() == ComplianceStatus.invalid && wasEverPublished(m)) {
                summary.put("items", List.of("该会议已作废"));
            } else {
                summary.put("items", List.of("会议尚未公示"));
            }
            summary.put("hint", "内部会议流程不可见，仅展示依法公开内容。");
            return summary;
        }
        if (isRecorder(ur)) return getRecorderTaskSummary(m);
        if (isChair(ur)) return getChairTaskSummary(m);
        return getMemberTaskSummary(m, ur);
    }

    private Map<String, Object> getRecorderTaskSummary(CommitteeMeeting m) {
        Map<String, Object> s = new HashMap<>();
        List<String> labels = getRecorderTodoLabels(m);
        if (!labels.isEmpty()) {
            s.put("level", "todo");
            s.put("title", "待处理");
            s.put("items", labels);
            s.put("hint", "这些事项由记录员维护或补录。");
        } else {
            s.put("level", "ok");
            s.put("title", "流程正常");
            s.put("items", List.of("暂无待补事项"));
            s.put("hint", m.getStage() == MeetingStage.ended ? "可查看纪要、公示和归档状态。" : "可继续查看送达、确认参会和佐证状态。");
        }
        return s;
    }

    private List<String> getRecorderTodoLabels(CommitteeMeeting m) {
        List<String> labels = new ArrayList<>();
        if (m.getStage() == MeetingStage.preparing) {
            DeliveryInfoVO info = getDeliveryInfo(m);
            if (Boolean.TRUE.equals(info.getNoDate())) labels.add("补填会议日期");
            if (info.getTotal() == 0) labels.add("选择通知对象并发送通知");
            if (info.getNoticeDone() < info.getTotal()) labels.add("补送 " + (info.getTotal() - info.getNoticeDone()) + " 人通知");
            if (info.getMaterialDone() < info.getTotal()) labels.add("补送 " + (info.getTotal() - info.getMaterialDone()) + " 人材料");
        } else if (m.getStage() == MeetingStage.ongoing) {
            RecordInfoVO info = getRecordInfo(m);
            long unsignedIn = info.getAttendances().stream().filter(a -> !a.getSignedIn()).count();
            if (unsignedIn > 0) labels.add("代录 " + unsignedIn + " 人确认参会");
            if (Boolean.TRUE.equals(info.getHasMajorIssue()) && !Boolean.TRUE.equals(info.getJuweiSigned())) {
                labels.add("补录居委会签字");
            }
            if (info.getEvidences().isEmpty()) labels.add("上传线下佐证");
        } else if (m.getStage() == MeetingStage.ended && m.getCompliance() != ComplianceStatus.invalid) {
            PublishInfoVO pi = getPublishInfo(m);
            if ("pending".equals(pi.getScoreState())) labels.add("发起公示");
            if ("overdue".equals(pi.getScoreState())) labels.add("逾期未公示");
        }
        return labels;
    }

    private Map<String, Object> getChairTaskSummary(CommitteeMeeting m) {
        Map<String, Object> s = new HashMap<>();
        if (m.getStage() == MeetingStage.preparing) {
            DeliveryInfoVO info = getDeliveryInfo(m);
            List<String> items = new ArrayList<>();
            if (Boolean.TRUE.equals(info.getNoDate())) items.add("补填会议日期");
            if (info.getTotal() == 0) items.add("选择通知对象并发送通知");
            if (info.getNoticeDone() < info.getTotal()) items.add((info.getTotal() - info.getNoticeDone()) + " 人通知未送达");
            if (info.getMaterialDone() < info.getTotal()) items.add((info.getTotal() - info.getMaterialDone()) + " 人材料未送达");
            if (!items.isEmpty()) {
                items.add("暂不可开始会议");
                s.put("level", "warn");
                s.put("title", "管理待处理");
                s.put("items", items);
                s.put("hint", "督促记录员完成送达后，主任/副主任才能开始会议。");
            } else {
                s.put("level", "todo");
                s.put("title", "可推进");
                s.put("items", List.of("可开始会议"));
                s.put("hint", "通知和材料已全部送达，可进入会议进行阶段。");
            }
        } else if (m.getStage() == MeetingStage.ongoing) {
            Map<String, Object> er = evaluateEndResult(m);
            String level = (String) er.get("level");
            if ("valid".equals(level)) {
                s.put("level", "todo");
                s.put("title", "可推进");
                s.put("items", List.of("可结束会议"));
                s.put("hint", "会议有效、记录完整，决议状态已确认。");
            } else {
                List<String> notes = (List<String>) er.get("notes");
                s.put("level", "warn");
                s.put("title", "管理待处理");
                s.put("items", notes.isEmpty() ? List.of("核查项未全部通过") : notes);
                s.put("hint", "可补齐后再结束，或按会议有效性/决议有效性带说明归档。");
            }
        } else {
            if (ComplianceStatus.invalid == m.getCompliance()) {
                s.put("level", "readonly");
                s.put("title", "已归档");
                s.put("items", List.of("会议未成立记录"));
                s.put("hint", "不计入已召开次数，可查看记录。");
            } else {
                PublishInfoVO pi = getPublishInfo(m);
                if ("pending".equals(pi.getScoreState())) {
                    s.put("level", "todo");
                    s.put("title", "待处理");
                    s.put("items", List.of("待公示，还剩 " + pi.getDaysLeft() + " 天"));
                    s.put("hint", "会议结束后三日内应完成公示。");
                } else if ("overdue".equals(pi.getScoreState())) {
                    s.put("level", "warn");
                    s.put("title", "逾期未公示");
                    s.put("items", List.of("已超过三日公示期限"));
                    s.put("hint", "计入年度考核扣分。");
                } else {
                    s.put("level", "ok");
                    s.put("title", "已完成");
                    s.put("items", List.of("会议已结束并完成公示"));
                    s.put("hint", "可查看会议纪要与公示记录。");
                }
            }
        }
        return s;
    }

    private Map<String, Object> getMemberTaskSummary(CommitteeMeeting m, UserRoleEntity ur) {
        Map<String, Object> s = new HashMap<>();
        if (m.getStage() == MeetingStage.preparing) {
            List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
            MeetingDelivery myDelivery = deliveries.stream()
                    .filter(d -> d.getUserRole().getId().equals(ur.getId()))
                    .findFirst().orElse(null);
            if (myDelivery == null) {
                // 委员不在当前送达名单中，但仍可查看并确认参会
                s.put("level", "todo");
                s.put("title", "待确认");
                s.put("items", List.of("会议通知尚未送达给你，但你可以提前确认参会"));
                s.put("hint", "主任发送通知时未将你列入名单，你仍可参会。");
            } else {
                List<String> items = new ArrayList<>();
                if (!myDelivery.getNoticeDelivered()) items.add("待记录员送达通知");
                if (!myDelivery.getMaterialDelivered()) items.add("待记录员送达材料");
                if (!items.isEmpty()) {
                    s.put("level", "wait");
                    s.put("title", "待关注");
                    s.put("items", items);
                    s.put("hint", "这一步由记录员完成送达，你暂时无需操作。");
                } else {
                    s.put("level", "ok");
                    s.put("title", "已处理");
                    s.put("items", List.of("通知和材料已送达"));
                    s.put("hint", "等待会议开始后再进行确认参会或表决。");
                }
            }
        } else if (m.getStage() == MeetingStage.ongoing) {
            MeetingRecord record = getRecord(m.getId());
            RecordAttendance myAtt = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), ur.getId())
                    .orElse(null);
            if (myAtt == null) {
                s.put("level", "todo");
                s.put("title", "待确认");
                s.put("items", List.of("请确认参会"));
                s.put("hint", "");
            } else {
                List<String> items = new ArrayList<>();
                if (!myAtt.getSignedIn()) items.add("我要确认参会");
                List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
                for (RecordTopic tp : topics) {
                    TopicVote vote = voteRepo.findByTopicIdAndUserRoleId(tp.getId(), ur.getId()).orElse(null);
                    if (vote == null) items.add("待表决 \"" + tp.getTitle() + "\"");
                }
                if (!items.isEmpty()) {
                    s.put("level", "todo");
                    s.put("title", "待处理");
                    s.put("items", items);
                    s.put("hint", "这是你本人可完成的会议动作。");
                } else {
                    s.put("level", "ok");
                    s.put("title", "已处理");
                    s.put("items", List.of("我的参会操作已完成"));
                    s.put("hint", "可继续查看会议进展和表决结果。");
                }
            }
        } else {
            s.put("level", "readonly");
            s.put("title", "可查看");
            s.put("items", List.of("会议已结束"));
            s.put("hint", "可查看会议纪要或公示结果。");
        }
        return s;
    }

    /**
     * 卡片进度（与小程序 utils/api/helpers.js getPersonalProgress 口径一致）：
     * 准备阶段→0/待开始；已结束→100/已归档；进行中→按本人确认参会+表决完成度算 0/50/100。
     * 简洁模式「我要办理」用 progress∈[0,100) 判定是否需要我处理。
     */
    private Map<String, Object> getCardProgress(CommitteeMeeting m, UserRoleEntity ur) {
        Map<String, Object> r = new HashMap<>();
        if (m.getStage() == MeetingStage.preparing) {
            r.put("pct", 0); r.put("label", "⏳ 待开始"); return r;
        }
        if (m.getStage() == MeetingStage.ended) {
            r.put("pct", 100); r.put("label", "📄 已归档"); return r;
        }
        // ongoing：本人是否确认参会 + 是否表决完所有议题
        boolean signedIn = false;
        boolean allVoted = false;
        MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
        if (record != null) {
            RecordAttendance myAtt = attendanceRepo
                    .findByRecordIdAndUserRoleId(record.getId(), ur.getId()).orElse(null);
            signedIn = myAtt != null && Boolean.TRUE.equals(myAtt.getSignedIn());
            List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
            allVoted = topics.isEmpty()
                    ? signedIn
                    : topics.stream().allMatch(tp ->
                        voteRepo.findByTopicIdAndUserRoleId(tp.getId(), ur.getId()).isPresent());
        }
        int done = (signedIn ? 1 : 0) + (allVoted ? 1 : 0);
        if (done == 0) { r.put("pct", 0); r.put("label", "🔴 待确认参会"); }
        else if (done == 1) { r.put("pct", 50); r.put("label", "🔶 待表决"); }
        else { r.put("pct", 100); r.put("label", "✅ 已完成"); }
        return r;
    }

    private String getRoleSummaryLine(CommitteeMeeting m, UserRoleEntity ur) {
        if (isChair(ur)) return getChairSummaryLine(m);
        Map<String, Object> taskSummary = getTaskSummary(m, ur);
        String level = (String) taskSummary.get("level");
        String title = (String) taskSummary.get("title");
        List<String> items = (List<String>) taskSummary.get("items");
        return title + "：" + String.join(" / ", items);
    }

    private String getChairSummaryLine(CommitteeMeeting m) {
        if (m.getStage() == MeetingStage.preparing) {
            DeliveryInfoVO info = getDeliveryInfo(m);
            if (Boolean.TRUE.equals(info.getNoDate())) return "⚠ 日期待定，需补填后发通知";
            if (info.getTotal() == 0) return "📨 待选择通知对象并发送通知";
            return "📨 通知 " + info.getNoticeDone() + "/" + info.getTotal() + " · 📎 材料 " + info.getMaterialDone() + "/" + info.getTotal();
        }
        if (m.getStage() == MeetingStage.ongoing) {
            RecordInfoVO info = getRecordInfo(m);
            int total = info.getAttendances().size();
            long signedIn = info.getAttendances().stream().filter(RecordInfoVO.AttendanceVO::getSignedIn).count();
            return "确认参会 " + signedIn + "/" + total;
        }
        if (ComplianceStatus.invalid == m.getCompliance()) return "✕ 会议不成立";
        PublishInfoVO pi = getPublishInfo(m);
        String pubTag = "ontime".equals(pi.getScoreState()) ? "已公示" :
                "overdue".equals(pi.getScoreState()) ? "逾期未公示" : "待公示";
        return "✓ 会议有效 · " + pubTag;
    }

    private boolean isRelevantToMember(CommitteeMeeting m, UserRoleEntity ur) {
        if (m.getStage() == MeetingStage.preparing) {
            // App 内送达过我，或本会议有微信通知留痕（0728：微信群发即全员被通知，不建送达记录）
            if (deliveryRepo.findByMeetingIdAndUserRoleId(m.getId(), ur.getId()).isPresent()) return true;
            return notificationLogRepo.findByMeetingIdOrderBySentAtAsc(m.getId()).stream()
                    .anyMatch(l -> l != null && "wechat".equals(l.getChannel()));
        }
        if (m.getStage() == MeetingStage.ongoing) {
            MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
            if (record == null) return false;
            return attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), ur.getId()).isPresent();
        }
        // ended 不再排除无效会议（0728）：委员端首页按「该期有没有开过会」判逾期，无效会议被藏掉会
        // 让已补开的期次一直挂着「去补开」催办；与主任端 0723「测试期无效会议保留可见」的决定对齐。
        return m.getStage() == MeetingStage.ended;
    }

    private static boolean isChair(UserRoleEntity ur) {
        // 这里表示“使用主任日常业务工作台”，并非法律身份判断。
        return ur.getRole().isCommitteeOperator();
    }
    private static boolean isRecorder(UserRoleEntity ur) {
        return ur.getRole().isRecorder();
    }
    private static boolean isExternal(UserRoleEntity ur) {
        return ur.getRole().isExternal();
    }
    private boolean isPublicMinutes(CommitteeMeeting m) {
        if (m.getStage() != MeetingStage.ended || m.getCompliance() == ComplianceStatus.invalid) {
            return false;
        }
        MeetingPublish pub = getPublish(m);
        return Boolean.TRUE.equals(pub.getPublished());
    }

    /**
     * 外部角色能否看到该会议的公示内容（见 产品边界定稿.md §2.1）。
     * - 业主：纪要公示后可见。
     * - 物业：不参与小区行政，一律不开放。
     */
    private boolean canSeePublished(UserRoleEntity ur, CommitteeMeeting m) {
        if (ur.getRole().isPropertyMgmt()) return false;
        return isPublicMinutes(m);
    }

    /** 对外部角色脱敏：仅保留状态，隐藏操作人/撤回原因等管理信息（见 §5）。 */
    private void redactPublishForExternal(PublishInfoVO vo) {
        vo.setPublishedBy(null);
        vo.setWithdrawnBy(null);
        vo.setWithdrawnAt(null);
        vo.setWithdrawReason(null);
    }

    /** 该会议是否曾被公示过（撤回/作废后此标记仍为真）。 */
    private boolean wasEverPublished(CommitteeMeeting m) {
        return getPublish(m).getPublishedAt() != null;
    }

    /**
     * 外部角色在列表/详情中能否看到该会议——含已撤回/已作废的 tombstone（见 §5）。
     * 物业不参与行政，一律不可见；业主仅能看到曾公示过的会议。
     */
    private boolean externalCanSeeMeeting(UserRoleEntity ur, CommitteeMeeting m) {
        if (ur.getRole().isPropertyMgmt()) return false;
        return wasEverPublished(m);
    }
    private static String getRoleView(UserRoleEntity ur) {
        if (isChair(ur)) return "chair";
        if (isRecorder(ur)) return "recorder";
        if (ur.getRole().isOwner()) return "owner";
        if (ur.getRole().isPropertyMgmt()) return "property";
        return "member";
    }
}
