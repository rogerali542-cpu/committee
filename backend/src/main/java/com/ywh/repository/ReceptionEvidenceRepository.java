package com.ywh.repository;

import com.ywh.entity.ReceptionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReceptionEvidenceRepository extends JpaRepository<ReceptionEvidence, Long> {
    List<ReceptionEvidence> findByRecordId(Long recordId);
    void deleteByRecordIdAndId(Long recordId, Long id);
}
