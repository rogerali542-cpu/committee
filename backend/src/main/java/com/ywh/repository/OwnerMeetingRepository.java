package com.ywh.repository;

import com.ywh.entity.OwnerMeeting;
import com.ywh.enums.MeetingStage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OwnerMeetingRepository extends JpaRepository<OwnerMeeting, Long> {
    List<OwnerMeeting> findByCommunityIdAndStageOrderByCreatedAtDesc(Long communityId, MeetingStage stage);
    List<OwnerMeeting> findByCommunityIdOrderByCreatedAtDesc(Long communityId);
}
