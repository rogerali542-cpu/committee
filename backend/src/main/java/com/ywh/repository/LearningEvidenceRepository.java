package com.ywh.repository;

import com.ywh.entity.LearningEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningEvidenceRepository extends JpaRepository<LearningEvidence, Long> {
    List<LearningEvidence> findByRecordId(Long recordId);
    void deleteByRecordIdAndId(Long recordId, Long id);
}
