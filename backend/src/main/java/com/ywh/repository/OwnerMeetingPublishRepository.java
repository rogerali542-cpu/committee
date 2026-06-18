package com.ywh.repository;

import com.ywh.entity.OwnerMeetingPublish;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwnerMeetingPublishRepository extends JpaRepository<OwnerMeetingPublish, Long> {
    Optional<OwnerMeetingPublish> findByMeetingId(Long meetingId);
}
