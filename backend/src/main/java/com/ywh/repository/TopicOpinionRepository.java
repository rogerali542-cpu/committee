package com.ywh.repository;

import com.ywh.entity.TopicOpinion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicOpinionRepository extends JpaRepository<TopicOpinion, Long> {
    List<TopicOpinion> findByTopicRecordIdOrderByCreatedAtAsc(Long recordId);
    List<TopicOpinion> findByTopicId(Long topicId);
    long countByTopicId(Long topicId);
}
