package com.ywh.repository;

import com.ywh.entity.RecordEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecordEvidenceRepository extends JpaRepository<RecordEvidence, Long> {
    List<RecordEvidence> findByRecordId(Long recordId);
}
