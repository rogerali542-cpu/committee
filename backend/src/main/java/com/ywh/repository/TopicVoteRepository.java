package com.ywh.repository;

import com.ywh.entity.TopicVote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TopicVoteRepository extends JpaRepository<TopicVote, Long> {
    List<TopicVote> findByTopicId(Long topicId);
    Optional<TopicVote> findByTopicIdAndUserRoleId(Long topicId, Long userRoleId);
}
