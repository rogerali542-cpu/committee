package com.ywh.repository;

import com.ywh.entity.LearningRecord;
import com.ywh.enums.LearningType;
import com.ywh.enums.MeetingStage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningRecordRepository extends JpaRepository<LearningRecord, Long> {
    List<LearningRecord> findByCommunityIdAndTypeOrderByDateDesc(Long communityId, LearningType type);
    List<LearningRecord> findByCommunityIdAndTypeInOrderByDateDesc(Long communityId, List<LearningType> types);
    List<LearningRecord> findByCommunityIdAndTypeAndStageOrderByDateDesc(Long communityId, LearningType type, MeetingStage stage);
    List<LearningRecord> findByCommunityIdAndTypeInAndStageOrderByDateDesc(Long communityId, List<LearningType> types, MeetingStage stage);
    List<LearningRecord> findByCommunityIdOrderByDateDesc(Long communityId);
}
