package com.ywh.repository;

import com.ywh.entity.CommitteeMeeting;
import com.ywh.enums.ComplianceStatus;
import com.ywh.enums.MeetingStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface CommitteeMeetingRepository extends JpaRepository<CommitteeMeeting, Long> {
    List<CommitteeMeeting> findByCommunityIdAndStageOrderByCreatedAtDesc(Long communityId, MeetingStage stage);
    List<CommitteeMeeting> findByCommunityIdAndStageAndComplianceNotOrderByCreatedAtDesc(Long communityId, MeetingStage stage, ComplianceStatus compliance);
    List<CommitteeMeeting> findByCommunityIdOrderByCreatedAtDesc(Long communityId);

    @Query("SELECT m FROM CommitteeMeeting m WHERE m.community.id = :communityId " +
           "AND m.stage IN :stages ORDER BY m.createdAt DESC")
    List<CommitteeMeeting> findByCommunityIdAndStageIn(Long communityId, List<MeetingStage> stages);

    @Query("SELECT COUNT(m) FROM CommitteeMeeting m WHERE m.community.id = :communityId " +
           "AND (m.stage = 'ongoing' OR m.stage = 'ended') AND (m.compliance IS NULL OR m.compliance <> 'invalid') " +
           "AND m.meetingDate IS NOT NULL AND m.meetingDate BETWEEN :start AND :end")
    long countValidMeetingsInPeriod(Long communityId, LocalDate start, LocalDate end);

    @Query("SELECT COUNT(m) FROM CommitteeMeeting m WHERE m.community.id = :communityId " +
           "AND m.stage = 'ended' AND (m.compliance IS NULL OR m.compliance <> 'invalid') " +
           "AND m.meetingDate IS NOT NULL AND YEAR(m.meetingDate) = :year")
    long countValidMeetingsInYear(Long communityId, int year);
}
