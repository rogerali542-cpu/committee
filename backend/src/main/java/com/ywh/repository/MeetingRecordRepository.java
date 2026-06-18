package com.ywh.repository;

import com.ywh.entity.MeetingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MeetingRecordRepository extends JpaRepository<MeetingRecord, Long> {
    Optional<MeetingRecord> findByMeetingId(Long meetingId);
}
