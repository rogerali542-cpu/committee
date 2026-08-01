package com.ywh.repository;

import com.ywh.entity.CommitteeMeeting;
import com.ywh.enums.ComplianceStatus;
import com.ywh.enums.MeetingStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CommitteeMeetingRepository extends JpaRepository<CommitteeMeeting, Long> {
    boolean existsByIdAndCommunityId(Long id, Long communityId);
    List<CommitteeMeeting> findByCommunityIdAndStageOrderByCreatedAtDesc(Long communityId, MeetingStage stage);
    List<CommitteeMeeting> findByCommunityIdAndStageAndComplianceNotOrderByCreatedAtDesc(Long communityId, MeetingStage stage, ComplianceStatus compliance);
    List<CommitteeMeeting> findByCommunityIdOrderByCreatedAtDesc(Long communityId);

    @Query("SELECT m FROM CommitteeMeeting m WHERE m.community.id = :communityId " +
           "AND m.stage IN :stages ORDER BY m.createdAt DESC")
    List<CommitteeMeeting> findByCommunityIdAndStageIn(Long communityId, List<MeetingStage> stages);

    // 防重复建会（0801）：同一小区、同一发起人、同名、仍在准备阶段、且创建于 :since 之后的会议。
    // createMeeting 用它做窄口径幂等——连点/超时重试不再建出两场一模一样的会。
    @Query("SELECT m FROM CommitteeMeeting m WHERE m.community.id = :communityId " +
           "AND m.createdBy = :userId AND m.title = :title AND m.stage = 'preparing' " +
           "AND m.createdAt >= :since ORDER BY m.createdAt DESC")
    List<CommitteeMeeting> findRecentSameTitle(Long communityId, Long userId, String title, LocalDateTime since);

    @Query("SELECT COUNT(m) FROM CommitteeMeeting m WHERE m.community.id = :communityId " +
           "AND (m.stage = 'ongoing' OR m.stage = 'ended') AND (m.compliance IS NULL OR m.compliance <> 'invalid') " +
           "AND m.meetingDate IS NOT NULL AND m.meetingDate BETWEEN :start AND :end")
    long countValidMeetingsInPeriod(Long communityId, LocalDate start, LocalDate end);

    @Query("SELECT COUNT(m) FROM CommitteeMeeting m WHERE m.community.id = :communityId " +
           "AND m.stage = 'ended' AND (m.compliance IS NULL OR m.compliance <> 'invalid') " +
           "AND m.meetingDate IS NOT NULL AND YEAR(m.meetingDate) = :year")
    long countValidMeetingsInYear(Long communityId, int year);
}
