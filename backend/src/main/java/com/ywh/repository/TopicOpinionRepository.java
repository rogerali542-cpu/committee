package com.ywh.repository;

import com.ywh.entity.TopicOpinion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicOpinionRepository extends JpaRepository<TopicOpinion, Long> {
    List<TopicOpinion> findByTopicRecordIdOrderByCreatedAtAsc(Long recordId);
    List<TopicOpinion> findByTopicId(Long topicId);
    long countByTopicId(Long topicId);
    // 「我这条填完了吗」（0803）：会议进行页按本人是否表过态判定议题已处理，需要区分他人的意见
    long countByTopicIdAndUserRoleId(Long topicId, Long userRoleId);
}
