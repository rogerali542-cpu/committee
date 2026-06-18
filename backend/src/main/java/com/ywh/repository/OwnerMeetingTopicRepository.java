package com.ywh.repository;

import com.ywh.entity.OwnerMeetingTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OwnerMeetingTopicRepository extends JpaRepository<OwnerMeetingTopic, Long> {
    List<OwnerMeetingTopic> findByRecordId(Long recordId);
}
