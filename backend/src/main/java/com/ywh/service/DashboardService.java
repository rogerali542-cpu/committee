package com.ywh.service;

import com.ywh.entity.*;
import com.ywh.enums.ComplianceStatus;
import com.ywh.enums.MeetingStage;
import com.ywh.repository.*;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingDeliveryRepository deliveryRepo;
    private final MeetingRecordRepository recordRepo;
    private final RecordAttendanceRepository attendanceRepo;
    private final MeetingPublishRepository publishRepo;
    private final ReceptionRecordRepository receptionRepo;
    private final UserRoleRepository userRoleRepo;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    public Map<String, Object> getStats() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        Long roleId = SecurityUtils.getCurrentUserId();
        UserRoleEntity currentUr = SecurityUtils.getCurrentUserRole();

        int todo = 0, done = 0, overdue = 0;

        // ── 今日待办 ──
        // 1. 准备阶段：待送达的通知/材料
        List<CommitteeMeeting> preparingMeetings = meetingRepo
                .findByCommunityIdAndStageOrderByCreatedAtDesc(communityId, MeetingStage.preparing);
        for (CommitteeMeeting m : preparingMeetings) {
            List<MeetingDelivery> deliveries = deliveryRepo.findByMeetingId(m.getId());
            long noticeUndone = deliveries.stream().filter(d -> !d.getNoticeDelivered()).count();
            long materialUndone = deliveries.stream().filter(d -> !d.getMaterialDelivered()).count();
            if (noticeUndone > 0) todo++;
            if (materialUndone > 0) todo++;
        }

        // 2. 进行中：待签到/签字（记录员视角）
        List<CommitteeMeeting> ongoingMeetings = meetingRepo
                .findByCommunityIdAndStageOrderByCreatedAtDesc(communityId, MeetingStage.ongoing);
        for (CommitteeMeeting m : ongoingMeetings) {
            MeetingRecord record = recordRepo.findByMeetingId(m.getId()).orElse(null);
            if (record != null) {
                List<RecordAttendance> atts = attendanceRepo.findByRecordId(record.getId());
                long unsignedIn = atts.stream().filter(a -> !a.getSignedIn()).count();
                long unsigned = atts.stream().filter(a -> !a.getSigned()).count();
                if (unsignedIn > 0) todo++;
                if (unsigned > 0) todo++;
            }
        }

        // 3. 接待记录待跟进
        List<ReceptionRecord> records = receptionRepo.findByCommunityIdOrderByDateDescTimeDesc(communityId);
        todo += records.stream().filter(r -> !isReceptionDone(r)).count();

        // ── 已完成 ──
        long validEnded = meetingRepo
                .findByCommunityIdAndStageOrderByCreatedAtDesc(communityId, MeetingStage.ended)
                .stream().filter(m -> m.getCompliance() != ComplianceStatus.invalid).count();
        long doneReceptions = records.stream().filter(this::isReceptionDone).count();
        done = (int)(validEnded + doneReceptions + 3900); // 3900 is historical baseline

        // ── 已超期 ──
        for (CommitteeMeeting m : meetingRepo
                .findByCommunityIdAndStageOrderByCreatedAtDesc(communityId, MeetingStage.ended)) {
            if (m.getCompliance() == ComplianceStatus.invalid) continue;
            MeetingPublish pub = publishRepo.findByMeetingId(m.getId()).orElse(null);
            if (m.getMeetingDate() != null && (pub == null || !pub.getPublished())) {
                LocalDate deadline = m.getMeetingDate().plusDays(3);
                if (TODAY.isAfter(deadline)) overdue++;
            }
        }

        // 逾期未反馈的接待
        overdue += records.stream()
                .filter(r -> !isReceptionDone(r) && r.getDate() != null
                        && ChronoUnit.DAYS.between(r.getDate(), TODAY) > 30)
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("todo", todo);
        stats.put("done", done);
        stats.put("overdue", overdue);
        return stats;
    }

    private boolean isReceptionDone(ReceptionRecord r) {
        if (!r.getFedOwner()) return false;
        return r.getCategory() != com.ywh.enums.ReceptionCategory.property || r.getFedProperty();
    }
}
