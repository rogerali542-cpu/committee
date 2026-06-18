package com.ywh.repository;

import com.ywh.entity.MeetingPublish;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MeetingPublishRepository extends JpaRepository<MeetingPublish, Long> {
    Optional<MeetingPublish> findByMeetingId(Long meetingId);
}
