package com.ywh.service;

import com.ywh.dto.CreateMeetingRequest;
import com.ywh.dto.MeetingDetailVO;
import com.ywh.dto.MeetingDetailVO.*;
import com.ywh.dto.ProxyActionRequest;
import com.ywh.dto.ProxyTargetVO;
import com.ywh.dto.RecordingVO;
import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickConfirmRequest;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.entity.*;
import com.ywh.enums.*;
import com.ywh.repository.*;
import com.ywh.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ObjectMapper objectMapper;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    // ===== List =====
    public List<Map<String, Object>> listMeetings(String stage) {
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

        return meetings.stream().map(m -> {
            Map<String, Object> card = new HashMap<>();
            card.put("id", m.getId());
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

        PublishInfoVO publishInfo = m.getStage() == MeetingStage.ended ? getPublishInfo(m) : null;
        if (publishInfo != null && isExternal(ur)) {
            redactPublishForExternal(publishInfo);
        }

        return MeetingDetailVO.builder()
                .id(m.getId())
                .title(m.getTitle())
                .meetingDate(m.getMeetingDate())
                .meetingTime(m.getMeetingTime())
                .location(m.getLocation())
                .description(m.getDescription())
                .stage(m.getStage())
                .compliance(m.getCompliance())
                .meetingMode(m.getMeetingMode())
                .userRole(ur.getRole().name())
                .userView(roleView)
                .coreLocked(m.getStage() != MeetingStage.preparing || m.getNotifiedAt() != null)
                .notifiedAt(m.getNotifiedAt() != null ? m.getNotifiedAt().toString() : null)
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
                .publish(publishInfo)
                .members(hideInternalRecord ? null : getMemberSummaries(m))
                .build();
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
                .stage(MeetingStage.preparing)
                .description(req.getDescription())
                .createdBy(userId)
                .build();
        applyGeneratedNoticeDraft(m);
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
        if (req.getDescription() != null) m.setDescription(req.getDescription());
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
            if (deliveries.isEmpty()) {
                throw new IllegalArgumentException("请先选择应参会人员并发送会议通知");
            }
            boolean allNoticesDone = deliveries.stream().allMatch(MeetingDelivery::getNoticeDelivered);
            boolean allMaterialsDone = deliveries.stream().allMatch(MeetingDelivery::getMaterialDelivered);
            if (!allNoticesDone || !allMaterialsDone) {
                throw new IllegalArgumentException("通知和材料尚未全部送达，不可开始会议");
            }
            // Initialize record
            MeetingRecord record = initRecord(m);
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
                // Auto-create re-do meeting
                createRecreation(m);
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

    /** 全部通知送达后记录"通知完成"时间，触发重大字段锁定（规则8）。 */
    private void markNotifiedIfComplete(Long meetingId) {
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(meetingId);
        boolean allNotified = !deliveries.isEmpty()
                && deliveries.stream().allMatch(MeetingDelivery::getNoticeDelivered);
        CommitteeMeeting m = meetingRepo.findById(meetingId).orElse(null);
        if (m == null) return;
        if (allNotified && m.getNotifiedAt() == null) {
            m.setNotifiedAt(LocalDateTime.now());
            meetingRepo.save(m);
        } else if (!allNotified && m.getNotifiedAt() != null) {
            // 撤回送达 → 解除锁定
            m.setNotifiedAt(null);
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
            a.setOperator(ur);
            a.setIsProxy(false);
            a.setOperatedAt(LocalDateTime.now());
        } else if ("cancel".equals(field)) {
            // 取消参会 → 回到未响应（既不确认也不缺席）
            a.setSignedIn(false);
            a.setDeclined(false);
            a.setOperator(ur);
            a.setIsProxy(false);
            a.setOperatedAt(LocalDateTime.now());
        } else if ("signed".equals(field) && a.getSignedIn()) {
            a.setSigned(true);
        }
        attendanceRepo.save(a);
    }

    @Transactional
    public void signAll(Long meetingId) {
        MeetingRecord record = getRecord(meetingId);
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        for (RecordAttendance a : attendances) {
            a.setSignedIn(true);
            a.setSigned(true);
        }
        attendanceRepo.saveAll(attendances);
        if (record.getHasMajorIssue()) {
            record.setJuweiSigned(true);
            recordRepo.save(record);
        }
    }

    // ===== Topics & Votes =====
    @Transactional
    public RecordTopic addTopic(Long meetingId, String title, String type,
                                 String decisionType, String options, Boolean realNameVote) {
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
                .build();
        return topicRepo.save(topic);
    }

    @Transactional
    public void removeTopic(Long meetingId, Long topicId) {
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
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

    @Transactional
    public void vote(Long meetingId, Long topicId, String choice, Long selectedId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (m.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅会议进行中可投票");
        }
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        Long urId = SecurityUtils.getCurrentUserId();
        MeetingRecord record = topic.getRecord();
        RecordAttendance attendance = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), urId)
                .orElseThrow(() -> new IllegalArgumentException("请先确认参会后再投票"));
        if (!Boolean.TRUE.equals(attendance.getSignedIn())) {
            throw new IllegalArgumentException("请先确认参会后再投票");
        }
        // 已投过票则不可更改
        TopicVote existing = voteRepo.findByTopicIdAndUserRoleId(topicId, urId).orElse(null);
        if (existing != null) {
            throw new IllegalArgumentException("已对该议题投票，不可更改");
        }
        TopicVote vote = TopicVote.builder()
                .topic(topic)
                .userRole(SecurityUtils.getCurrentUserRole())
                .operator(SecurityUtils.getCurrentUserRole())
                .isProxy(false)
                .operatedAt(LocalDateTime.now())
                .build();
        // simple: 存 choice（for_vote/against/abstain）; multi_choice: 存 selectedId
        if (selectedId != null) {
            vote.setSelectedId(selectedId);
        } else if (choice != null && !choice.isEmpty()) {
            vote.setChoice(VoteChoice.valueOf(choice));
        }
        voteRepo.save(vote);
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
            if (!isVoteTopic(topic)) {
                continue;
            }
            // 快速模式：票数以主持人现场确认为准（聚合值存于 quickConfirmJson），
            // 不再强制"同意+反对+弃权 == 签到人数"——避免因人数对不上而无法结束会议。
            // 仅清理旧的逐人投票记录，不再合成逐人投票。
            voteRepo.deleteAll(voteRepo.findByTopicId(topic.getId()));
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
    public Long saveRecording(Long meetingId, String url, String fileName, Long fileSize) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        UserRoleEntity uploader = SecurityUtils.getCurrentUserRole();
        MeetingRecording recording = MeetingRecording.builder()
                .meeting(meeting)
                .uploader(uploader)
                .recordingUrl(url)
                .fileName(fileName)
                .fileSize(fileSize)
                .asrStatus("none")
                .build();
        recording = recordingRepo.save(recording);
        return recording.getId();
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
        materialRepo.save(MeetingMaterial.builder()
                .meetingId(meetingId)
                .fileName(fileName)
                .fileType(fileType)
                .sizeText(sizeText)
                .fileUrl(fileUrl)
                .build());
    }

    @Transactional
    public void removeMaterial(Long meetingId, Long materialId) {
        materialRepo.deleteByMeetingIdAndId(meetingId, materialId);
    }

    private List<Map<String, Object>> buildMaterials(Long meetingId) {
        return materialRepo.findByMeetingId(meetingId).stream().map(mat -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", mat.getId());
            m.put("name", mat.getFileName());
            m.put("sizeText", mat.getSizeText());
            m.put("fileType", mat.getFileType());
            m.put("url", mat.getFileUrl());
            return m;
        }).collect(Collectors.toList());
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

    // ===== Publish =====
    @Transactional
    public void publish(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        PublishInfoVO info = getPublishInfo(m);
        if (info.getDaysLeft() != null && info.getDaysLeft() < 0) {
            throw new IllegalArgumentException("已超过会议结束后三日公示期限，不再补公示");
        }
        MeetingPublish pub = publishRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("公示记录不存在"));
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        pub.setPublished(true);
        pub.setPublishDate(TODAY);
        pub.setPublishedById(ur.getId());
        pub.setPublishedByName(ur.getRealName());
        pub.setPublishedAt(LocalDateTime.now());
        // 重新公示：清除撤回标记
        pub.setWithdrawn(false);
        publishRepo.save(pub);
        // 快照本次公示的纪要版本，使"历史版本"能定位到被公示的内容
        snapshotRevision(meetingId, generateMinutes(meetingId));
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
        sb.append("会议名称：").append(nullToUnknown(m.getTitle())).append('\n');
        sb.append("会议时间：").append(nullToUnknown(m.getMeetingDate())).append(" ").append(nullToUnknown(m.getMeetingTime())).append('\n');
        sb.append("会议地点：").append(nullToUnknown(m.getLocation())).append('\n');
        sb.append("会议说明：").append(nullToUnknown(m.getDescription())).append('\n');
        sb.append("主持人：").append(host).append('\n');
        sb.append("应到委员：").append(attendances.size()).append("人\n");
        sb.append("实到委员：").append(present.size()).append("人；名单：")
                .append(present.isEmpty() ? "未明确说明" : present.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、"))).append('\n');
        if (!absent.isEmpty()) {
            sb.append("缺席委员：").append(absent.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、"))).append('\n');
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
        sb.append("请生成正式《业主委员会会议纪要》，比议题报告更精简。");
        sb.append("每个议题控制在一小段，通报类只根据人工确认议题报告精简为通报内容、委员知悉/意见和后续安排，严禁写赞成、反对、通过、未通过或表决；");
        sb.append("讨论类写明主要意见、共识和后续安排；表决/决议类只写方案要点、票数、表决结果、决议和关键执行安排，不展开过多背景细节。");
        sb.append("优先依据人工确认结果和议题报告摘录，不要逐句复述转写，不要输出冗长背景。");
        sb.append("缺失信息写“未明确说明”，不得编造。");
        return sb.toString();
    }

    private String quickResultLabel(String result) {
        if ("passed".equals(result)) return "通过";
        if ("rejected".equals(result)) return "未通过";
        if ("abstain".equals(result)) return "弃权";
        if ("unclear".equals(result)) return "未明确说明";
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
        minutesRevisionRepo.deleteAll(minutesRevisionRepo.findByMeetingIdOrderByVersionNoDesc(meetingId));
        publishRepo.findByMeetingId(meetingId).ifPresent(publishRepo::delete);
        recordRepo.findByMeetingId(meetingId).ifPresent(record -> {
            List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
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
        String title = "关于召开" + (m.getTitle() != null ? m.getTitle() : "业主委员会会议") + "的通知";
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.add("会议时间：" + (m.getMeetingDate() != null ? m.getMeetingDate() : "待定")
                + " " + (m.getMeetingTime() != null ? m.getMeetingTime() : ""));
        lines.add("会议地点：" + (m.getLocation() != null ? m.getLocation() : "待定"));
        // 主要议题：按准备会议时添加的议题标题，逐条编号列出（1.xxx 换行 2.xxx）
        String topicsText = buildNoticeTopicsText(m);
        if (!topicsText.isBlank()) {
            lines.add("主要议题：");
            lines.add(topicsText);
        } else if (m.getDescription() != null && !m.getDescription().isBlank()) {
            lines.add("主要议题：" + m.getDescription());
        } else {
            lines.add("主要议题：待补充");
        }
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
        if (operator == null || !operator.getRole().isChair()) {
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
        if (req.getProofUrl() == null || req.getProofUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("请上传代录凭证");
        }
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
            if (voteRepo.findByTopicIdAndUserRoleId(topic.getId(), memberId).isPresent()) {
                invalid.add(attendance.getUserRole().getRealName() + "已投票");
                continue;
            }

            TopicVote vote = TopicVote.builder()
                    .topic(topic)
                    .userRole(attendance.getUserRole())
                    .operator(operator)
                    .isProxy(true)
                    .proofUrl(req.getProofUrl())
                    .operatedAt(now)
                    .build();
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
        int total = attendances.size();

        List<RecordInfoVO.AttendanceVO> attendanceVOs = attendances.stream().map(a -> {
            RecordInfoVO.AttendanceVO av = new RecordInfoVO.AttendanceVO();
            av.setUserRoleId(a.getUserRole().getId());
            av.setName(a.getUserRole().getRealName());
            av.setRole(a.getUserRole().getRole().name());
            av.setRoomNumber(a.getUserRole().getRoomNumber());
            av.setSignedIn(a.getSignedIn());
            av.setSigned(a.getSigned());
            av.setDeclined(Boolean.TRUE.equals(a.getDeclined()));
            av.setIsSelf(ur.getId().equals(a.getUserRole().getId()));
            av.setIsProxy(Boolean.TRUE.equals(a.getIsProxy()));
            av.setOperatorName(a.getOperator() != null ? a.getOperator().getRealName() : null);
            av.setProofUrl(a.getProofUrl());
            return av;
        }).collect(Collectors.toList());

        Map<Long, QuickConfirmRequest.TopicResult> quickConfirmTopics = quickConfirmTopicMap(record);
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
                forV = Optional.ofNullable(quickResult.getForVotes()).orElse(0);
                agV = Optional.ofNullable(quickResult.getAgVotes()).orElse(0);
                abV = Optional.ofNullable(quickResult.getAbVotes()).orElse(0);
                countedVotes = forV + agV + abV;
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
            tv.setTotal(total);
            tv.setNeed(need);
            tv.setPassed(passed);
            tv.setStatus(!voteRequired ? "recorded" : (passed ? "passed" : (countedVotes < total ? "pending" : "failed")));
            tv.setText(statusText);

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
            return deliveryRepo.findByMeetingIdAndUserRoleId(m.getId(), ur.getId()).isPresent();
        }
        if (m.getStage() == MeetingStage.ongoing) {
            MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
            if (record == null) return false;
            return attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), ur.getId()).isPresent();
        }
        return m.getStage() == MeetingStage.ended && m.getCompliance() != ComplianceStatus.invalid;
    }

    private static boolean isChair(UserRoleEntity ur) {
        return ur.getRole().isChair();
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
