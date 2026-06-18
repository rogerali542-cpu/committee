package com.ywh.repository;

import com.ywh.entity.OwnerMeetingEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OwnerMeetingEvidenceRepository extends JpaRepository<OwnerMeetingEvidence, Long> {
    List<OwnerMeetingEvidence> findByRecordId(Long recordId);
}
