package com.ywh.repository;

import com.ywh.entity.RecordTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecordTopicRepository extends JpaRepository<RecordTopic, Long> {
    List<RecordTopic> findByRecordIdOrderBySortOrder(Long recordId);
}
