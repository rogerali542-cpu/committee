package com.ywh.service;

import com.ywh.entity.*;
import com.ywh.enums.ComplianceStatus;
import com.ywh.enums.MeetingMode;
import com.ywh.enums.MeetingStage;
import com.ywh.enums.TopicType;
import com.ywh.repository.*;
import com.ywh.util.SecurityUtils;
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
public class OwnerMeetingService {

    private final OwnerMeetingRepository omRepo;
    private final OwnerMeetingNotifyRepository notifyRepo;
    private final OwnerMeetingBallotRepository ballotRepo;
    private final OwnerMeetingRecordRepository recordRepo;
    private final OwnerMeetingTopicRepository topicRepo;
    private final OwnerMeetingPublishRepository ownerPublishRepo;
    private final OwnerMinutesRevisionRepository ownerRevisionRepo;
    private final OwnerMeetingEvidenceRepository evidenceRepo;
    private final HousingUnitRepository housingUnitRepo;
    private final HousingUnitOwnerRepository housingUnitOwnerRepo;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    public List<Map<String, Object>> listMeetings(String stage) {
        // 物业不参与小区行政，不开放业主大会
        if (SecurityUtils.isPropertyMgmt()) {
            return Collections.emptyList();
        }
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<OwnerMeeting> meetings;
        if (stage != null) {
            meetings = omRepo.findByCommunityIdAndStageOrderByCreatedAtDesc(communityId, MeetingStage.valueOf(stage));
        } else {
            meetings = omRepo.findByCommunityIdOrderByCreatedAtDesc(communityId);
        }
        return meetings.stream().map(m -> {
            Map<String, Object> card = new HashMap<>();
            card.put("id", m.getId());
            card.put("title", m.getTitle());
            card.put("meetingDate", m.getMeetingDate());
            card.put("meetingTime", m.getMeetingTime());
            card.put("location", m.getLocation());
            card.put("type", m.getType());
            card.put("stage", m.getStage().name());
            card.put("compliance", m.getCompliance() != null ? m.getCompliance().name() : null);
            card.put("needsVote", m.getNeedsVote());
            card.put("statusLine", getStatusLine(m));
            return card;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getDetail(Long meetingId) {
        if (SecurityUtils.isPropertyMgmt()) {
            throw new IllegalArgumentException("物业不参与小区行政事务，无权查看业主大会");
        }
        OwnerMeeting m = omRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("大会不存在"));
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", m.getId());
        detail.put("title", m.getTitle());
        detail.put("meetingDate", m.getMeetingDate());
        detail.put("meetingTime", m.getMeetingTime());
        detail.put("location", m.getLocation());
        detail.put("type", m.getType());
        detail.put("stage", m.getStage().name());
        detail.put("compliance", m.getCompliance() != null ? m.getCompliance().name() : null);
        detail.put("meetingMode", m.getMeetingMode() != null ? m.getMeetingMode().name() : "normal");
        detail.put("description", m.getDescription());
        detail.put("totalOwners", m.getTotalOwners());
        detail.put("totalArea", m.getTotalArea());
        detail.put("needsVote", m.getNeedsVote());

        // 房屋花名册含全体产权人身份（个人级明细），仅治理角色可见（见 §4）。
        // 业主投票校验走 /my-units、/units/{id}/can-vote（自身范围），无需全量花名册。
        if (!SecurityUtils.isExternal()) {
            detail.put("housingUnits", getHousingUnitList(m));
        }

        if (m.getStage() == MeetingStage.preparing) {
            detail.put("notifyInfo", getNotifyInfo(m));
            if (Boolean.TRUE.equals(m.getNeedsVote())) {
                detail.put("ballotInfo", getBallotInfo(m));
            }
        } else if (m.getStage() == MeetingStage.ongoing) {
            detail.put("recordInfo", getRecordInfo(m));
        } else {
            detail.put("complianceInfo", getComplianceInfo(m));
            if (m.getCompliance() != ComplianceStatus.invalid) {
                detail.put("publishInfo", getPublishInfo(m));
            }
        }

        return detail;
    }

    @Transactional
    public OwnerMeeting createMeeting(Map<String, Object> req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        // 从房屋花名册实时取总户数和总面积（按户代表制）
        long totalOwners = housingUnitRepo.countByCommunity(communityId);
        int totalArea = housingUnitRepo.sumAreaByCommunity(communityId).intValue();
        OwnerMeeting m = OwnerMeeting.builder()
                .community(Community.builder().id(communityId).build())
                .title((String) req.get("title"))
                .meetingDate(parseDate((String) req.get("meetingDate")))
                .meetingTime(parseTime((String) req.get("meetingTime")))
                .location((String) req.getOrDefault("location", "待定"))
                .type((String) req.getOrDefault("type", "regular"))
                .stage(MeetingStage.preparing)
                .description((String) req.get("description"))
                .totalOwners((int) totalOwners)
                .totalArea(totalArea)
                .needsVote((Boolean) req.getOrDefault("needsVote", true))
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();
        m = omRepo.save(m);

        // Init notify
        notifyRepo.save(OwnerMeetingNotify.builder()
                .meeting(m).sentCount(0).announced(false).contentComplete(false).build());

        if (Boolean.TRUE.equals(m.getNeedsVote())) {
            ballotRepo.save(OwnerMeetingBallot.builder()
                    .meeting(m).deliveredCount(0).recordsComplete(false).nonFaceAnnounce(false).build());
        }

        return m;
    }

    @Transactional
    public void advance(Long meetingId, String action, String mode) {
        OwnerMeeting m = omRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("大会不存在"));
        if ("start".equals(action)) {
            OwnerMeetingNotify notify = notifyRepo.findByMeetingId(meetingId).orElse(null);
            if (notify == null || !notify.getAnnounced()) {
                throw new IllegalArgumentException("请先标记公告已发布后再开始大会");
            }
            if (notify.getSentCount() < m.getTotalOwners()) {
                // Warn but allow
            }
            m.setStage(MeetingStage.ongoing);
            m.setMeetingMode("quick".equals(mode) ? MeetingMode.quick : MeetingMode.normal);
            initOwnerRecord(m);
        } else if ("end".equals(action)) {
            Map<String, Object> er = evaluateEndResult(m);
            String level = (String) er.get("level");
            if ("invalid".equals(level)) {
                m.setCompliance(ComplianceStatus.invalid);
            } else if ("flawed".equals(level)) {
                m.setCompliance(ComplianceStatus.flawed);
            } else {
                m.setCompliance(ComplianceStatus.valid);
            }
            m.setStage(MeetingStage.ended);
            OwnerMeetingPublish pub = ownerPublishRepo.findByMeetingId(meetingId)
                    .orElse(OwnerMeetingPublish.builder().meeting(m).published(false).withdrawn(false).build());
            if (pub.getId() == null) ownerPublishRepo.save(pub);
        }
        omRepo.save(m);
    }

    @Transactional
    public void toggleNotify(Long meetingId, String field) {
        OwnerMeetingNotify n = notifyRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("通知记录不存在"));
        if ("announced".equals(field)) n.setAnnounced(!n.getAnnounced());
        else if ("contentComplete".equals(field)) n.setContentComplete(!n.getContentComplete());
        notifyRepo.save(n);
    }

    @Transactional
    public void notifyAll(Long meetingId) {
        OwnerMeetingNotify n = notifyRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("通知记录不存在"));
        OwnerMeeting m = omRepo.findById(meetingId).orElseThrow();
        // 从 housing_units 取总户数作为 sentCount
        long totalOwners = housingUnitRepo.countByCommunity(m.getCommunity().getId());
        n.setSentCount((int) totalOwners);
        notifyRepo.save(n);
        // TODO: 通知实体建好后，遍历所有 housing_unit_owners 给每人写通知记录
        // List<Long> allOwnerIds = housingUnitOwnerRepo.findDistinctOwnerIdsByCommunity(m.getCommunity().getId());
    }

    @Transactional
    public void toggleBallot(Long meetingId, String field) {
        OwnerMeetingBallot b = ballotRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("表决票记录不存在"));
        if ("records".equals(field)) b.setRecordsComplete(!b.getRecordsComplete());
        else if ("nonFaceAnnounce".equals(field)) b.setNonFaceAnnounce(!b.getNonFaceAnnounce());
        ballotRepo.save(b);
    }

    @Transactional
    public void ballotAll(Long meetingId) {
        OwnerMeetingBallot b = ballotRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("表决票记录不存在"));
        OwnerMeeting m = omRepo.findById(meetingId).orElseThrow();
        b.setDeliveredCount(m.getTotalOwners());
        ballotRepo.save(b);
    }

    @Transactional
    public void adjustCount(Long meetingId, String field, int delta) {
        OwnerMeetingRecord r = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        OwnerMeeting m = omRepo.findById(meetingId).orElseThrow();
        if ("presentOwners".equals(field)) {
            r.setPresentOwners(Math.max(0, Math.min(m.getTotalOwners(), r.getPresentOwners() + delta)));
        } else if ("presentArea".equals(field)) {
            r.setPresentArea(Math.max(0, Math.min(m.getTotalArea(), r.getPresentArea() + delta)));
        }
        recordRepo.save(r);
    }

    @Transactional
    public void toggleSupervisor(Long meetingId) {
        OwnerMeetingRecord r = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        r.setSupervisorSigned(!r.getSupervisorSigned());
        recordRepo.save(r);
    }

    @Transactional
    public void toggleProcess(Long meetingId, String key) {
        OwnerMeetingRecord r = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        switch (key) {
            case "designated" -> r.setDesignated(!r.getDesignated());
            case "monitor" -> r.setMonitor(!r.getMonitor());
            case "callout" -> r.setCallout(!r.getCallout());
            case "tally" -> r.setTally(!r.getTally());
        }
        recordRepo.save(r);
    }

    @Transactional
    public OwnerMeetingTopic addTopic(Long meetingId, String title, String type) {
        OwnerMeeting m = omRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("大会不存在"));
        if (m.getStage() != MeetingStage.ongoing) {
            throw new IllegalArgumentException("仅大会进行中可添加表决议题");
        }
        if (!Boolean.TRUE.equals(m.getNeedsVote())) {
            throw new IllegalArgumentException("本次大会不涉及表决事项，不能添加议题");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("议题名称不能为空");
        }
        OwnerMeetingRecord r = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        return topicRepo.save(OwnerMeetingTopic.builder()
                .record(r)
                .title(title.trim())
                .type("major".equals(type) ? TopicType.major : TopicType.ordinary)
                .forOwners(0).agOwners(0).abOwners(0)
                .forArea(0).agArea(0).abArea(0)
                .build());
    }

    @Transactional
    public void voteAdj(Long meetingId, Long topicId, String field, int delta) {
        OwnerMeetingTopic tp = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        OwnerMeeting m = omRepo.findById(meetingId).orElseThrow();
        int max = field.contains("Area") ? m.getTotalArea() : m.getTotalOwners();
        switch (field) {
            case "forOwners" -> tp.setForOwners(Math.max(0, Math.min(max, tp.getForOwners() + delta)));
            case "agOwners" -> tp.setAgOwners(Math.max(0, Math.min(max, tp.getAgOwners() + delta)));
            case "abOwners" -> tp.setAbOwners(Math.max(0, Math.min(max, tp.getAbOwners() + delta)));
            case "forArea" -> tp.setForArea(Math.max(0, Math.min(max, tp.getForArea() + delta)));
            case "agArea" -> tp.setAgArea(Math.max(0, Math.min(max, tp.getAgArea() + delta)));
            case "abArea" -> tp.setAbArea(Math.max(0, Math.min(max, tp.getAbArea() + delta)));
        }
        topicRepo.save(tp);
    }

    @Transactional
    public void addEvidence(Long meetingId, String fileName, String fileType) {
        OwnerMeetingRecord r = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        evidenceRepo.save(OwnerMeetingEvidence.builder()
                .record(r).fileName(fileName).fileType(fileType).build());
    }

    @Transactional
    public void publish(Long meetingId) {
        OwnerMeeting m = omRepo.findById(meetingId).orElseThrow();
        Map<String, Object> pi = getPublishInfo(m);
        if ((int) pi.get("daysLeft") < 0) {
            throw new IllegalArgumentException("已超过公示期限，不可补公示。");
        }
        OwnerMeetingPublish pub = ownerPublishRepo.findByMeetingId(meetingId)
                .orElse(OwnerMeetingPublish.builder().meeting(m).published(false).withdrawn(false).build());
        if (pub.getId() == null) pub = ownerPublishRepo.save(pub);
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        pub.setPublished(true);
        pub.setPublishDate(TODAY);
        pub.setPublishedById(ur.getId());
        pub.setPublishedByName(ur.getRealName());
        pub.setPublishedAt(LocalDateTime.now());
        pub.setWithdrawn(false);
        ownerPublishRepo.save(pub);
        snapshotRevision(meetingId, generateMinutes(meetingId));
    }

    /** 撤回业主大会公示（见 产品边界定稿.md §5）。必须填写原因并留痕。 */
    @Transactional
    public void withdrawPublish(Long meetingId, String reason) {
        omRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("大会不存在"));
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("撤回公示必须填写原因");
        }
        OwnerMeetingPublish pub = ownerPublishRepo.findByMeetingId(meetingId)
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
        ownerPublishRepo.save(pub);
    }

    public String generateMinutes(Long meetingId) {
        OwnerMeeting m = omRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Owner meeting not found"));
        if (SecurityUtils.isPropertyMgmt()) {
            throw new IllegalArgumentException("物业不参与小区行政事务，无权查看业主大会");
        }
        if (SecurityUtils.isExternal() && !isPublicMinutes(m)) {
            throw new IllegalArgumentException("Minutes are not published yet");
        }

        OwnerMeetingRecord r = recordRepo.findByMeetingId(meetingId).orElse(null);
        if (r != null && r.getMinutesText() != null && !r.getMinutesText().isBlank()) {
            return r.getMinutesText();
        }

        int totalOwners = m.getTotalOwners() != null ? m.getTotalOwners() : 0;
        int totalArea = m.getTotalArea() != null ? m.getTotalArea() : 0;
        int presentOwners = r != null && r.getPresentOwners() != null ? r.getPresentOwners() : 0;
        int presentArea = r != null && r.getPresentArea() != null ? r.getPresentArea() : 0;
        List<OwnerMeetingTopic> topics = r != null ? topicRepo.findByRecordId(r.getId()) : Collections.emptyList();

        StringBuilder sb = new StringBuilder();
        sb.append("业主大会会议纪要\n\n");
        sb.append("大会名称：").append(m.getTitle()).append("\n");
        sb.append("大会时间：").append(m.getMeetingDate()).append(" ").append(m.getMeetingTime()).append("\n");
        sb.append("大会地点：").append(m.getLocation()).append("\n");
        sb.append("大会类型：").append("special".equals(m.getType()) ? "临时大会" : "定期大会").append("\n\n");

        sb.append("一、到场情况\n");
        sb.append("到场 ").append(presentOwners).append(" 户，面积 ")
                .append(formatWan(presentArea)).append(" 万㎡。\n");
        sb.append("总户数 ").append(totalOwners).append("，总面积 ")
                .append(formatWan(totalArea)).append(" 万㎡。\n");

        if (!topics.isEmpty()) {
            sb.append("\n二、表决情况\n");
            int idx = 1;
            for (OwnerMeetingTopic t : topics) {
                boolean passed = isOwnerTopicPassed(m, t);
                sb.append(idx++).append(". ").append(t.getTitle()).append("（")
                        .append(t.getType() == TopicType.major ? "重大事项" : "普通决议").append("）\n");
                sb.append("   投票结果：赞成 ").append(valueOrZero(t.getForOwners())).append(" 户/")
                        .append(formatWan(valueOrZero(t.getForArea()))).append(" 万㎡，反对 ")
                        .append(valueOrZero(t.getAgOwners())).append(" 户/")
                        .append(formatWan(valueOrZero(t.getAgArea()))).append(" 万㎡，弃权 ")
                        .append(valueOrZero(t.getAbOwners())).append(" 户/")
                        .append(formatWan(valueOrZero(t.getAbArea()))).append(" 万㎡。\n");
                sb.append("   表决结果：").append(passed ? "决议通过" : "未通过").append("\n");
            }
        }

        sb.append("\n三、大会结论\n");
        if (m.getCompliance() == ComplianceStatus.invalid) {
            sb.append("大会无效，决议不生效。\n");
        } else if (m.getCompliance() == ComplianceStatus.flawed) {
            sb.append("大会有效，部分记录或决议事项需说明后归档。\n");
        } else {
            sb.append("大会有效，决议按表决结果执行。\n");
        }
        return sb.toString();
    }

    @Transactional
    public void updateMinutes(Long meetingId, String text) {
        OwnerMeeting m = omRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Owner meeting not found"));
        if (isPublicMinutes(m)) {
            throw new IllegalArgumentException("Published minutes cannot be edited directly. Please withdraw or create a revision.");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Minutes text cannot be empty");
        }
        OwnerMeetingRecord r = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Owner meeting record not found"));
        r.setMinutesText(text);
        recordRepo.save(r);
        snapshotRevision(meetingId, text);
    }

    /** 追加一条纪要修订快照，版本号自增；内容与上一版相同则跳过。见 §5 */
    private void snapshotRevision(Long meetingId, String content) {
        OwnerMinutesRevision last = ownerRevisionRepo
                .findFirstByMeetingIdOrderByVersionNoDesc(meetingId).orElse(null);
        if (last != null && Objects.equals(last.getContent(), content)) {
            return;
        }
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        ownerRevisionRepo.save(OwnerMinutesRevision.builder()
                .meetingId(meetingId)
                .versionNo(last == null ? 1 : last.getVersionNo() + 1)
                .content(content)
                .editorId(ur != null ? ur.getId() : null)
                .editorName(ur != null ? ur.getRealName() : null)
                .build());
    }

    /** 业主大会纪要修订版本历史，仅治理角色可见。见 §5 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMinutesRevisions(Long meetingId) {
        if (SecurityUtils.isExternal()) {
            throw new IllegalArgumentException("无权查看纪要修订历史");
        }
        omRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("大会不存在"));
        return ownerRevisionRepo.findByMeetingIdOrderByVersionNoDesc(meetingId).stream()
                .map(r -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("versionNo", r.getVersionNo());
                    vo.put("editorName", r.getEditorName());
                    vo.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                    vo.put("content", r.getContent());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeMeeting(Long meetingId) {
        omRepo.deleteById(meetingId);
    }

    public Map<String, Object> getStats() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<OwnerMeeting> all = omRepo.findByCommunityIdOrderByCreatedAtDesc(communityId);
        List<OwnerMeeting> ended = all.stream()
                .filter(m -> (m.getStage() == MeetingStage.ongoing || m.getStage() == MeetingStage.ended)
                        && m.getCompliance() != ComplianceStatus.invalid)
                .toList();
        long annual = ended.stream().filter(m -> "regular".equals(m.getType())
                && m.getMeetingDate() != null && m.getMeetingDate().getYear() == TODAY.getYear()).count();
        long special = ended.stream().filter(m -> "special".equals(m.getType())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("annualCount", annual);
        stats.put("specialCount", special);
        stats.put("preparing", all.stream().filter(m -> m.getStage() == MeetingStage.preparing).count());
        stats.put("ongoing", all.stream().filter(m -> m.getStage() == MeetingStage.ongoing).count());
        stats.put("ended", all.stream().filter(m -> m.getStage() == MeetingStage.ended
                && m.getCompliance() != ComplianceStatus.invalid).count());
        return stats;
    }

    // ================= Private Helpers =================

    private void initOwnerRecord(OwnerMeeting m) {
        OwnerMeetingRecord r = OwnerMeetingRecord.builder()
                .meeting(m)
                .presentOwners(0).presentArea(0)
                .supervisorName("（街道办/居委会监督员）")
                .supervisorSigned(false)
                .designated(false).monitor(false).callout(false).tally(false)
                .build();
        recordRepo.save(r);
    }

    private Map<String, Object> evaluateEndResult(OwnerMeeting m) {
        Map<String, Object> result = new HashMap<>();
        if (Boolean.FALSE.equals(m.getNeedsVote())) {
            result.put("level", "valid");
            result.put("notes", Collections.emptyList());
            return result;
        }

        OwnerMeetingRecord r = recordRepo.findByMeetingId(m.getId()).orElse(null);
        if (r == null) {
            result.put("level", "flawed");
            result.put("notes", List.of("缺少会议记录"));
            return result;
        }

        int presentO = r.getPresentOwners();
        int presentA = r.getPresentArea();
        int needO = m.getTotalOwners() / 2 + 1;
        int needA = m.getTotalArea() / 2 + 1;

        if (presentO < needO || presentA < needA) {
            List<String> why = new ArrayList<>();
            if (presentO < needO) why.add("到场业主仅" + presentO + "户（需≥" + needO + "户）");
            if (presentA < needA) why.add("到场面积仅" + (presentA / 10000.0) + "万m²（需≥" + (needA / 10000.0) + "万m²）");
            result.put("level", "invalid");
            result.put("notes", List.of("未达双过半法定人数：" + String.join("；", why)));
            return result;
        }

        List<OwnerMeetingTopic> topics = topicRepo.findByRecordId(r.getId());
        List<String> notes = new ArrayList<>();
        for (OwnerMeetingTopic tp : topics) {
            boolean passO = tp.getForOwners() >= needO;
            boolean passA = tp.getForArea() >= needA;
            if (!passO || !passA) {
                notes.add("\"" + tp.getTitle() + "\"决议未通过（"
                        + (tp.getType() == TopicType.major ? "重大事项需双2/3" : "普通决议需双过半") + "）");
            }
        }
        result.put("level", notes.isEmpty() ? "valid" : "flawed");
        result.put("notes", notes);
        return result;
    }

    private String getStatusLine(OwnerMeeting m) {
        if (m.getStage() == MeetingStage.preparing) {
            OwnerMeetingNotify n = notifyRepo.findByMeetingId(m.getId()).orElse(null);
            int sent = n != null ? n.getSentCount() : 0;
            return "通知进度 " + sent + "/" + m.getTotalOwners() + " 户";
        }
        if (m.getStage() == MeetingStage.ongoing) return "进行中...";
        if (ComplianceStatus.invalid == m.getCompliance()) return "会议未成立";
        OwnerMeetingPublish pub = ownerPublishRepo.findByMeetingId(m.getId()).orElse(null);
        if (pub != null && pub.getPublished()) return "已公示（" + pub.getPublishDate() + "）";
        if (pub != null && Boolean.TRUE.equals(pub.getWithdrawn())) return "已撤回公示";
        return "待公示";
    }

    private Map<String, Object> getNotifyInfo(OwnerMeeting m) {
        OwnerMeetingNotify n = notifyRepo.findByMeetingId(m.getId()).orElse(null);
        Map<String, Object> info = new HashMap<>();
        if (m.getMeetingDate() == null) {
            info.put("deadlineStr", "待定");
            info.put("daysLeft", null);
            info.put("sent", n != null ? n.getSentCount() : 0);
            info.put("total", m.getTotalOwners());
        } else {
            LocalDate deadline = m.getMeetingDate().minusDays(15);
            int daysLeft = (int) ChronoUnit.DAYS.between(TODAY, deadline);
            info.put("deadlineStr", deadline.toString());
            info.put("daysLeft", daysLeft);
            info.put("sent", n != null ? n.getSentCount() : 0);
            info.put("total", m.getTotalOwners());
        }
        info.put("announced", n != null && n.getAnnounced());
        info.put("contentComplete", n != null && n.getContentComplete());
        int sent = n != null ? n.getSentCount() : 0;
        info.put("pct", Math.min(100, Math.round(sent * 100.0 / m.getTotalOwners())));
        return info;
    }

    private Map<String, Object> getBallotInfo(OwnerMeeting m) {
        OwnerMeetingBallot b = ballotRepo.findByMeetingId(m.getId()).orElse(null);
        Map<String, Object> info = new HashMap<>();
        int delivered = b != null ? b.getDeliveredCount() : 0;
        if (m.getMeetingDate() != null) {
            LocalDate deadline = m.getMeetingDate().minusDays(7);
            int daysLeft = (int) ChronoUnit.DAYS.between(TODAY, deadline);
            info.put("deadlineStr", deadline.toString());
            info.put("daysLeft", daysLeft);
        }
        info.put("delivered", delivered);
        info.put("total", m.getTotalOwners());
        info.put("pct", Math.min(100, Math.round(delivered * 100.0 / m.getTotalOwners())));
        info.put("recordsComplete", b != null && b.getRecordsComplete());
        info.put("nonFaceAnnounce", b != null && b.getNonFaceAnnounce());
        return info;
    }

    private Map<String, Object> getRecordInfo(OwnerMeeting m) {
        OwnerMeetingRecord r = recordRepo.findByMeetingId(m.getId()).orElse(null);
        Map<String, Object> info = new HashMap<>();
        int presentO = r != null ? r.getPresentOwners() : 0;
        int presentA = r != null ? r.getPresentArea() : 0;
        int needO = m.getTotalOwners() / 2 + 1;
        int needA = m.getTotalArea() / 2 + 1;

        info.put("presentOwners", presentO);
        info.put("presentArea", presentA);
        info.put("needOwners", needO);
        info.put("needArea", needA);
        info.put("quorumOk", presentO >= needO && presentA >= needA);
        info.put("okOwners", presentO >= needO);
        info.put("okArea", presentA >= needA);
        info.put("supervisorName", r != null ? r.getSupervisorName() : "");
        info.put("supervisorSigned", r != null && r.getSupervisorSigned());
        info.put("process", r != null ? Map.of(
                "designated", r.getDesignated(),
                "monitor", r.getMonitor(),
                "callout", r.getCallout(),
                "tally", r.getTally()) : Collections.emptyMap());

        if (r != null) {
            List<OwnerMeetingTopic> topics = topicRepo.findByRecordId(r.getId());
            info.put("topics", topics.stream().map(tp -> {
                Map<String, Object> t = new HashMap<>();
                t.put("id", tp.getId());
                t.put("title", tp.getTitle());
                t.put("type", tp.getType().name());
                t.put("forOwners", tp.getForOwners());
                t.put("agOwners", tp.getAgOwners());
                t.put("abOwners", tp.getAbOwners());
                t.put("forArea", tp.getForArea());
                t.put("agArea", tp.getAgArea());
                t.put("abArea", tp.getAbArea());
                t.put("passOwners", tp.getForOwners() >= needO);
                t.put("passArea", tp.getForArea() >= needA);
                t.put("passed", tp.getForOwners() >= needO && tp.getForArea() >= needA);
                return t;
            }).collect(Collectors.toList()));
        }

        return info;
    }

    private Map<String, Object> getComplianceInfo(OwnerMeeting m) {
        Map<String, Object> info = new HashMap<>();
        info.put("compliance", m.getCompliance() != null ? m.getCompliance().name() : "valid");
        return info;
    }

    private Map<String, Object> getPublishInfo(OwnerMeeting m) {
        OwnerMeetingPublish pub = ownerPublishRepo.findByMeetingId(m.getId()).orElse(null);
        LocalDate base = m.getMeetingDate() != null ? m.getMeetingDate() : TODAY;
        LocalDate deadline = base.plusDays(3);
        int daysLeft = (int) ChronoUnit.DAYS.between(TODAY, deadline);

        Map<String, Object> info = new HashMap<>();
        info.put("published", pub != null && pub.getPublished());
        info.put("publishDate", pub != null ? (pub.getPublishDate() != null ? pub.getPublishDate().toString() : null) : null);
        info.put("deadlineStr", deadline.toString());
        info.put("daysLeft", daysLeft);
        info.put("scoreState", pub != null && pub.getPublished() ? (pub.getPublishDate() != null
                && !pub.getPublishDate().isAfter(deadline) ? "ontime" : "late")
                : (daysLeft < 0 ? "overdue" : "pending"));

        // 公示状态机（见 §5）
        boolean published = pub != null && Boolean.TRUE.equals(pub.getPublished());
        boolean withdrawn = pub != null && Boolean.TRUE.equals(pub.getWithdrawn());
        info.put("status", published ? "published" : withdrawn ? "withdrawn" : "pending");
        info.put("withdrawn", withdrawn);
        boolean external = SecurityUtils.isExternal();
        // 对外脱敏：仅保留状态，隐藏操作人/撤回原因（管理信息）
        info.put("publishedBy", external ? null : (pub != null ? pub.getPublishedByName() : null));
        info.put("publishedAt", external ? null : (pub != null && pub.getPublishedAt() != null ? pub.getPublishedAt().toString() : null));
        info.put("withdrawnBy", external ? null : (pub != null ? pub.getWithdrawnByName() : null));
        info.put("withdrawnAt", external ? null : (pub != null && pub.getWithdrawnAt() != null ? pub.getWithdrawnAt().toString() : null));
        info.put("withdrawReason", external ? null : (pub != null ? pub.getWithdrawReason() : null));
        return info;
    }

    private boolean isPublicMinutes(OwnerMeeting m) {
        return m.getStage() == MeetingStage.ended
                && m.getCompliance() != ComplianceStatus.invalid
                && ownerPublishRepo.findByMeetingId(m.getId())
                        .map(OwnerMeetingPublish::getPublished)
                        .orElse(false);
    }

    private boolean isOwnerTopicPassed(OwnerMeeting m, OwnerMeetingTopic topic) {
        int needOwners = valueOrZero(m.getTotalOwners()) / 2 + 1;
        int needArea = valueOrZero(m.getTotalArea()) / 2 + 1;
        return valueOrZero(topic.getForOwners()) >= needOwners
                && valueOrZero(topic.getForArea()) >= needArea;
    }

    private int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private String formatWan(int area) {
        return String.format(Locale.US, "%.1f", area / 10000.0);
    }

    private LocalDate parseDate(String s) { return s != null && !s.isEmpty() ? LocalDate.parse(s) : null; }
    private java.time.LocalTime parseTime(String s) { return s != null && !s.isEmpty() ? java.time.LocalTime.parse(s) : null; }

    /** 房屋花名册（含代表人 + 所有产权人） */
    private List<Map<String, Object>> getHousingUnitList(OwnerMeeting m) {
        return housingUnitRepo.findByCommunityId(m.getCommunity().getId()).stream()
                .map(u -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", u.getId());
                    item.put("building", u.getBuilding());
                    item.put("unitNo", u.getUnitNo());
                    item.put("area", u.getArea());
                    item.put("representativeUserId", u.getRepresentativeUserId());
                    // 所有产权人
                    item.put("ownerUserIds", housingUnitOwnerRepo.findOwnerIdsByUnitId(u.getId()));
                    return item;
                })
                .collect(Collectors.toList());
    }

    /** 获取当前用户代表的所有房屋（用于投票聚合） */
    public List<HousingUnit> getUserRepresentedUnits(Long communityId, Long userRoleId) {
        return housingUnitRepo.findByCommunityIdAndRepresentativeUserId(communityId, userRoleId);
    }

    /** 校验当前用户是否为指定户的代表人 */
    public boolean isHouseholdRepresentative(Long unitId, Long userRoleId) {
        return housingUnitRepo.findById(unitId)
                .map(u -> userRoleId.equals(u.getRepresentativeUserId()))
                .orElse(false);
    }
}
