package com.ywh.repository;

import com.ywh.entity.MeetingNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MeetingNotificationLogRepository extends JpaRepository<MeetingNotificationLog, Long> {
    List<MeetingNotificationLog> findByMeetingIdOrderBySentAtAsc(Long meetingId);
}
