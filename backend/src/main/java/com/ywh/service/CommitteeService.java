package com.ywh.service;

import com.ywh.dto.CreateMeetingRequest;
import com.ywh.dto.MeetingDetailVO;
import com.ywh.dto.MeetingDetailVO.*;
import com.ywh.dto.ProxyActionRequest;
import com.ywh.dto.ProxyTargetVO;
import com.ywh.entity.*;
import com.ywh.enums.*;
import com.ywh.repository.*;
import com.ywh.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRoleRepository userRoleRepo;
    private final ObjectMapper objectMapper;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    // ===== List =====
    public List<Map<String, Object>> listMeetings(String stage) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        UserRoleEntity currentUr = SecurityUtils.getCurrentUserRole();

        List<CommitteeMeeting> meetings;
        if (isExternal(currentUr)) {
            // External: only published ended meetings with valid compliance
            meetings = meetingRepo.findByCommunityIdAndStageAndComplianceNotOrderByCreatedAtDesc(
                    communityId, MeetingStage.valueOf(stage), ComplianceStatus.invalid);
            meetings = meetings.stream()
                    .filter(m -> getPublish(m).getPublished())
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
            return card;
        }).collect(Collectors.toList());
    }

    // ===== Detail =====
    @Transactional
    public MeetingDetailVO getDetail(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        String roleView = getRoleView(ur);
        Map<String, Object> taskSummary = getTaskSummary(m, ur);

        return MeetingDetailVO.builder()
                .id(m.getId())
                .title(m.getTitle())
                .meetingDate(m.getMeetingDate())
                .meetingTime(m.getMeetingTime())
                .location(m.getLocation())
                .description(m.getDescription())
                .stage(m.getStage())
                .compliance(m.getCompliance())
                .userRole(ur.getRole().name())
                .userView(roleView)
                .taskLevel((String) taskSummary.get("level"))
                .taskTitle((String) taskSummary.get("title"))
                .taskItems((List<String>) taskSummary.get("items"))
                .taskHint((String) taskSummary.get("hint"))
                .flowNodeText(getFlowNodeText(m))
                .delivery(m.getStage() == MeetingStage.preparing ? getDeliveryInfo(m) : null)
                .record(m.getStage() != MeetingStage.preparing ? getRecordInfo(m) : null)
                .publish(m.getStage() == MeetingStage.ended ? getPublishInfo(m) : null)
                .members(getMemberSummaries(m))
                .build();
    }

    // ===== Create =====
    @Transactional
    public CommitteeMeeting createMeeting(CreateMeetingRequest req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        Long userId = SecurityUtils.getCurrentUserId();
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
        m = meetingRepo.save(m);

        // Initialize deliveries for all committee members
        List<UserRoleEntity> committeeMembers = userRoleRepo.findByCommunityIdAndRoleIn(
                communityId, List.of("主任", "副主任", "委员"));
        for (UserRoleEntity member : committeeMembers) {
            deliveryRepo.save(MeetingDelivery.builder()
                    .meeting(m)
                    .userRole(member)
                    .noticeDelivered(false)
                    .materialDelivered(false)
                    .build());
        }

        return m;
    }

    // ===== Advance Stage =====
    @Transactional
    public void advanceStage(Long meetingId, String action) {
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
            boolean allNoticesDone = deliveries.stream().allMatch(MeetingDelivery::getNoticeDelivered);
            boolean allMaterialsDone = deliveries.stream().allMatch(MeetingDelivery::getMaterialDelivered);
            if (!allNoticesDone || !allMaterialsDone) {
                throw new IllegalArgumentException("通知和材料尚未全部送达，不可开始会议");
            }
            // Initialize record
            initRecord(m);
            m.setStage(MeetingStage.ongoing);

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
                                .build());
                publishRepo.save(pub);
            }
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
    }

    @Transactional
    public void sendAll(Long meetingId) {
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(meetingId);
        for (MeetingDelivery d : deliveries) {
            d.setNoticeDelivered(true);
            d.setMaterialDelivered(true);
        }
        deliveryRepo.saveAll(deliveries);
    }

    // ===== Attendance (sign-in/sign) =====
    @Transactional
    public void toggleAttendance(Long meetingId, Long userRoleId, String field) {
        MeetingRecord record = getRecord(meetingId);
        RecordAttendance a = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), userRoleId)
                .orElseThrow(() -> new IllegalArgumentException("签到记录不存在"));
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
        MeetingRecord record = getRecord(meetingId);
        Long urId = SecurityUtils.getCurrentUserId();
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        RecordAttendance a = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), urId)
                .orElseThrow(() -> new IllegalArgumentException("你不在本次会议委员名单中"));
        if ("signedIn".equals(field)) {
            a.setSignedIn(true);
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
                                 String decisionType, String options) {
        MeetingRecord record = getRecord(meetingId);
        TopicType tType = "major".equals(type) ? TopicType.major : TopicType.decision;
        String dt = decisionType != null && !decisionType.isEmpty() ? decisionType : "simple";
        RecordTopic topic = RecordTopic.builder()
                .record(record)
                .title(title)
                .type(tType)
                .decisionType(dt)
                .optionsJson(options)
                .sortOrder((int) topicRepo.findByRecordIdOrderBySortOrder(record.getId()).size() + 1)
                .build();
        return topicRepo.save(topic);
    }

    @Transactional
    public void removeTopic(Long meetingId, Long topicId) {
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        topicRepo.delete(topic);
    }

    @Transactional
    public void vote(Long meetingId, Long topicId, String choice, Long selectedId) {
        RecordTopic topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        Long urId = SecurityUtils.getCurrentUserId();
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

        // Also mark as signed in if not already
        MeetingRecord record = topic.getRecord();
        attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), urId)
                .ifPresent(a -> {
                    if (!Boolean.TRUE.equals(a.getSignedIn())) {
                        a.setSignedIn(true);
                        a.setOperator(SecurityUtils.getCurrentUserRole());
                        a.setIsProxy(false);
                        a.setOperatedAt(LocalDateTime.now());
                    }
                    attendanceRepo.save(a);
                });
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

    // ===== Evidence =====
    @Transactional
    public void addEvidence(Long meetingId, String fileName, String fileType) {
        MeetingRecord record = getRecord(meetingId);
        evidenceRepo.save(RecordEvidence.builder()
                .record(record)
                .fileName(fileName)
                .fileType(fileType)
                .build());
    }

    @Transactional
    public void removeEvidence(Long meetingId, Long evidenceId) {
        evidenceRepo.deleteById(evidenceId);
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
        pub.setPublished(true);
        pub.setPublishDate(TODAY);
        publishRepo.save(pub);
    }

    // ===== Minutes =====
    public String generateMinutes(Long meetingId) {
        CommitteeMeeting m = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        MeetingRecord record = getRecord(meetingId);
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());

        int total = attendances.size();
        int need = total / 2 + 1;
        List<RecordAttendance> present = attendances.stream().filter(RecordAttendance::getSignedIn).toList();
        List<RecordAttendance> absent = attendances.stream().filter(a -> !a.getSignedIn()).toList();
        int signedN = (int) attendances.stream().filter(RecordAttendance::getSigned).count();
        String host = attendances.stream()
                .filter(a -> a.getUserRole().getRole().isChair())
                .findFirst()
                .map(a -> a.getUserRole().getRealName())
                .orElse(attendances.get(0).getUserRole().getRealName());
        boolean presentHalf = present.size() >= need;
        boolean hasVote = !topics.isEmpty();
        boolean draft = m.getStage() != MeetingStage.ended;

        Map<String, Object> er = m.getStage() == MeetingStage.ended
                ? evaluateEndResult(m) : evaluateEndResult(m);
        String concClass = (String) er.get("level");
        String concText = er.containsKey("conclusion") ? (String) er.get("conclusion")
                : computeConclusionText(er);

        StringBuilder sb = new StringBuilder();
        sb.append("业主委员会会议纪要\n\n");
        sb.append("会议名称：").append(m.getTitle()).append("\n");
        sb.append("会议时间：").append(m.getMeetingDate()).append(" ").append(m.getMeetingTime()).append("\n");
        sb.append("会议地点：").append(m.getLocation()).append("\n");
        sb.append("主持人：").append(host).append("\n");
        sb.append("记录人：秘书小李\n\n");

        sb.append("一、参会情况\n");
        sb.append("应到委员 ").append(total).append(" 人，实到 ").append(present.size()).append(" 人，");
        sb.append(presentHalf ? "已过半，达到法定人数" : "未过半，未达法定人数").append("。\n");
        sb.append("出席：").append(present.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、"))).append("\n");
        if (!absent.isEmpty()) {
            sb.append("缺席：").append(absent.stream().map(a -> a.getUserRole().getRealName()).collect(Collectors.joining("、"))).append("\n");
        }
        if (record.getHasMajorIssue()) {
            sb.append("列席：").append(record.getJuweiName()).append("（居委会委员）\n");
        }
        sb.append("\n二、会议议题\n");
        sb.append(m.getDescription() != null ? m.getDescription() : "（无）").append("\n");

        if (hasVote) {
            sb.append("\n三、表决情况\n");
            int idx = 1;
            for (RecordTopic tp : topics) {
                List<TopicVote> votes = voteRepo.findByTopicId(tp.getId());
                int forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
                int agV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.against).count();
                int abV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.abstain).count();
                String statusText = getTopicStatusText(tp, total, forV, agV, abV);
                sb.append(idx).append(". 【").append(tp.getType() == TopicType.major ? "重大事项" : "决定事项").append("】").append(tp.getTitle()).append("\n");
                sb.append("   赞成 ").append(forV).append(" 票，反对 ").append(agV).append(" 票，弃权 ").append(abV).append(" 票（赞成需≥").append(need).append("）。表决结果：").append(statusText).append("。\n");
                idx++;
            }
        }

        sb.append("\n").append(hasVote ? "四" : "三").append("、签字确认\n");
        sb.append("会议记录经 ").append(signedN).append("/").append(total).append(" 名委员签字确认。\n");
        if (record.getHasMajorIssue()) {
            sb.append("重大事项").append(record.getJuweiSigned() ? "已" : "尚未").append("由居委会委员（").append(record.getJuweiName()).append("）签字。\n");
        }

        sb.append("\n").append(hasVote ? "五" : "四").append("、会议结论\n");
        sb.append(concText).append("\n");

        return sb.toString();
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
        if (m.getStage() != MeetingStage.preparing) {
            throw new IllegalArgumentException("仅准备阶段的会议可以取消");
        }
        meetingRepo.delete(m);
    }

    // ================= Private Helpers =================

    private void initRecord(CommitteeMeeting m) {
        MeetingRecord record = MeetingRecord.builder()
                .meeting(m)
                .hasDecision(false)
                .hasMajorIssue(false)
                .juweiName("王红梅（社区居委会）")
                .juweiSigned(false)
                .build();
        record = recordRepo.save(record);

        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
        for (MeetingDelivery d : deliveries) {
            attendanceRepo.save(RecordAttendance.builder()
                    .record(record)
                    .userRole(d.getUserRole())
                    .signedIn(false)
                    .signed(false)
                    .build());
        }
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
        List<UserRoleEntity> committeeMembers = userRoleRepo.findByCommunityIdAndRoleIn(
                m.getCommunity().getId(), List.of("主任", "副主任", "委员"));
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
            throw new IllegalArgumentException("以下成员已签到，不能重复代录：" + String.join("、", duplicated));
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
                invalid.add(attendance.getUserRole().getRealName() + "未签到");
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
                .orElse(MeetingPublish.builder().meeting(m).published(false).build());
    }

    private DeliveryInfoVO getDeliveryInfo(CommitteeMeeting m) {
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
        int noticeDone = (int) deliveries.stream().filter(MeetingDelivery::getNoticeDelivered).count();
        int materialDone = (int) deliveries.stream().filter(MeetingDelivery::getMaterialDelivered).count();
        boolean allDone = noticeDone == deliveries.size() && materialDone == deliveries.size();

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
                    return md;
                }).collect(Collectors.toList());

        DeliveryInfoVO vo = new DeliveryInfoVO();
        vo.setDeadlineStr(deadlineStr);
        vo.setDaysLeft(daysLeft);
        vo.setNoticeDone(noticeDone);
        vo.setMaterialDone(materialDone);
        vo.setTotal(deliveries.size());
        vo.setAllDone(allDone);
        vo.setNoDate(meetingDate == null);
        vo.setMemberDeliveries(memberDeliveries);
        return vo;
    }

    private RecordInfoVO getRecordInfo(CommitteeMeeting m) {
        MeetingRecord record = getRecord(m.getId());
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
            av.setIsSelf(ur.getId().equals(a.getUserRole().getId()));
            av.setIsProxy(Boolean.TRUE.equals(a.getIsProxy()));
            av.setOperatorName(a.getOperator() != null ? a.getOperator().getRealName() : null);
            av.setProofUrl(a.getProofUrl());
            return av;
        }).collect(Collectors.toList());

        List<RecordInfoVO.TopicVO> topicVOs = topics.stream().map(tp -> {
            List<TopicVote> votes = voteRepo.findByTopicId(tp.getId());
            int need = total / 2 + 1;
            String decisionType = tp.getDecisionType() != null ? tp.getDecisionType() : "simple";
            List<Map<String, Object>> options = parseTopicOptions(tp);
            int forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
            int agV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.against).count();
            int abV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.abstain).count();
            boolean passed;
            String statusText;

            if ("multi_choice".equals(decisionType)) {
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
                        : (votes.size() < total ? "待继续表决" : "决议未通过");
            } else {
                passed = forV >= need;
                statusText = getTopicStatusText(tp, total, forV, agV, abV);
            }

            RecordInfoVO.TopicVO tv = new RecordInfoVO.TopicVO();
            tv.setId(tp.getId());
            tv.setTitle(tp.getTitle());
            tv.setType(tp.getType().name());
            tv.setDecisionType(decisionType);
            tv.setOptions(options);
            tv.setForVotes(forV);
            tv.setAgVotes(agV);
            tv.setAbVotes(abV);
            tv.setTotal(total);
            tv.setNeed(need);
            tv.setPassed(passed);
            tv.setStatus(passed ? "passed" : (votes.size() < total ? "pending" : "failed"));
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
            return tv;
        }).collect(Collectors.toList());

        List<RecordInfoVO.EvidenceVO> evidenceVOs = evidences.stream().map(e -> {
            RecordInfoVO.EvidenceVO ev = new RecordInfoVO.EvidenceVO();
            ev.setId(e.getId());
            ev.setFileName(e.getFileName());
            ev.setFileType(e.getFileType());
            return ev;
        }).collect(Collectors.toList());

        // Check results
        int need = total / 2 + 1;
        int signedIn = (int) attendances.stream().filter(RecordAttendance::getSignedIn).count();
        int signed = (int) attendances.stream().filter(RecordAttendance::getSigned).count();

        List<RecordInfoVO.CheckVO> checks = new ArrayList<>();
        RecordInfoVO.CheckVO c1 = new RecordInfoVO.CheckVO();
        c1.setLabel("委员签到过半");
        c1.setDetail(signedIn + "/" + total + "（需≥" + need + "）");
        c1.setOk(signedIn >= need);
        checks.add(c1);

        RecordInfoVO.CheckVO c2 = new RecordInfoVO.CheckVO();
        c2.setLabel("会议记录委员签字过半");
        c2.setDetail(signed + "/" + total + "（需≥" + need + "）");
        c2.setOk(signed >= need);
        checks.add(c2);

        if (record.getHasDecision()) {
            RecordInfoVO.CheckVO c3 = new RecordInfoVO.CheckVO();
            c3.setLabel("决定事项·过半委员签字");
            c3.setDetail(signed + "/" + total + "（需≥" + need + "）");
            c3.setOk(signed >= need);
            checks.add(c3);
        }
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
        vo.setAttendances(attendanceVOs);
        vo.setTopics(topicVOs);
        vo.setEvidences(evidenceVOs);
        vo.setChecks(checks);
        vo.setRecordLevel(recordLevel);
        vo.setRecordText(recordText);
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
        return vo;
    }

    private List<MemberSummaryVO> getMemberSummaries(CommitteeMeeting m) {
        List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
        return deliveries.stream().map(d -> {
            MemberSummaryVO ms = new MemberSummaryVO();
            ms.setUserRoleId(d.getUserRole().getId());
            ms.setName(d.getUserRole().getRealName());
            ms.setRole(d.getUserRole().getRole().name());
            return ms;
        }).collect(Collectors.toList());
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
        int signed = (int) attendances.stream().filter(RecordAttendance::getSigned).count();

        Map<String, Object> result = new HashMap<>();
        List<String> notes = new ArrayList<>();

        if (signedIn < need) {
            notes.add("未达法定人数：仅 " + signedIn + "/" + total + " 名委员签到（需≥" + need + "），会议不成立");
            result.put("level", "invalid");
            result.put("notes", notes);
            result.put("conclusion", "会议未达法定人数，会议不成立，本次决议不生效。");
            return result;
        }

        if (signed < need) {
            notes.add((record.getHasDecision() ? "决定事项记录待补正" : "会议记录待补正")
                    + "：委员签字未过半（" + signed + "/" + total + "，需≥" + need + "）");
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
        for (RecordTopic tp : topics) {
            List<TopicVote> votes = voteRepo.findByTopicId(tp.getId());
            int forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
            if (forV < need) {
                int voted = votes.size();
                if (voted < total) {
                    notes.add("\"" + tp.getTitle() + "\"尚未完成表决（已投 " + voted + "/" + total + "，赞成 " + forV + "/" + need + "）");
                } else {
                    notes.add("\"" + tp.getTitle() + "\"未通过（赞成 " + forV + "/" + need + "）");
                }
            }
        }

        boolean allComplete = signed >= need
                && (!record.getHasMajorIssue() || record.getJuweiSigned())
                && !evidences.isEmpty()
                && topics.stream().allMatch(tp -> {
                    List<TopicVote> votes = voteRepo.findByTopicId(tp.getId());
                    int forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
                    return forV >= need;
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
            long signed = info.getAttendances().stream().filter(RecordInfoVO.AttendanceVO::getSigned).count();
            if (signedIn < total || signed < total) return "进行中 · 签到/签字确认中";
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
            if (m.getStage() == MeetingStage.ended && m.getCompliance() != ComplianceStatus.invalid
                    && getPublish(m).getPublished()) {
                summary.put("items", List.of("可查看已公示纪要与决定"));
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
            s.put("hint", m.getStage() == MeetingStage.ended ? "可查看纪要、公示和归档状态。" : "可继续查看送达、签到、签字和佐证状态。");
        }
        return s;
    }

    private List<String> getRecorderTodoLabels(CommitteeMeeting m) {
        List<String> labels = new ArrayList<>();
        if (m.getStage() == MeetingStage.preparing) {
            DeliveryInfoVO info = getDeliveryInfo(m);
            if (Boolean.TRUE.equals(info.getNoDate())) labels.add("补填会议日期");
            if (info.getNoticeDone() < info.getTotal()) labels.add("补送 " + (info.getTotal() - info.getNoticeDone()) + " 人通知");
            if (info.getMaterialDone() < info.getTotal()) labels.add("补送 " + (info.getTotal() - info.getMaterialDone()) + " 人材料");
        } else if (m.getStage() == MeetingStage.ongoing) {
            RecordInfoVO info = getRecordInfo(m);
            long unsignedIn = info.getAttendances().stream().filter(a -> !a.getSignedIn()).count();
            long unsigned = info.getAttendances().stream().filter(a -> !a.getSigned()).count();
            if (unsignedIn > 0) labels.add("代录 " + unsignedIn + " 人签到");
            if (unsigned > 0) labels.add("代录 " + unsigned + " 人签字");
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
                s.put("level", "readonly");
                s.put("title", "可查看");
                s.put("items", List.of("你不在本会委员名单中"));
                s.put("hint", "");
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
                    s.put("hint", "等待会议开始后再进行签到、签字或表决。");
                }
            }
        } else if (m.getStage() == MeetingStage.ongoing) {
            MeetingRecord record = getRecord(m.getId());
            RecordAttendance myAtt = attendanceRepo.findByRecordIdAndUserRoleId(record.getId(), ur.getId())
                    .orElse(null);
            if (myAtt == null) {
                s.put("level", "readonly");
                s.put("title", "可查看");
                s.put("items", List.of("你不在本次会议委员名单中"));
                s.put("hint", "");
            } else {
                List<String> items = new ArrayList<>();
                if (!myAtt.getSignedIn()) items.add("我要签到");
                if (myAtt.getSignedIn() && !myAtt.getSigned()) items.add("我要签字");
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
            return "📨 通知 " + info.getNoticeDone() + "/" + info.getTotal() + " · 📎 材料 " + info.getMaterialDone() + "/" + info.getTotal();
        }
        if (m.getStage() == MeetingStage.ongoing) {
            RecordInfoVO info = getRecordInfo(m);
            int total = info.getAttendances().size();
            long signedIn = info.getAttendances().stream().filter(RecordInfoVO.AttendanceVO::getSignedIn).count();
            long signed = info.getAttendances().stream().filter(RecordInfoVO.AttendanceVO::getSigned).count();
            return "签到 " + signedIn + "/" + total + " · 签字 " + signed + "/" + total;
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
    private static String getRoleView(UserRoleEntity ur) {
        if (isChair(ur)) return "chair";
        if (isRecorder(ur)) return "recorder";
        if (ur.getRole().isOwner()) return "owner";
        if (ur.getRole().isPropertyMgmt()) return "property";
        return "member";
    }
}
