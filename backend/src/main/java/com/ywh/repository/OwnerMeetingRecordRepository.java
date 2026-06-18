package com.ywh.repository;

import com.ywh.entity.OwnerMeetingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwnerMeetingRecordRepository extends JpaRepository<OwnerMeetingRecord, Long> {
    Optional<OwnerMeetingRecord> findByMeetingId(Long meetingId);
}
