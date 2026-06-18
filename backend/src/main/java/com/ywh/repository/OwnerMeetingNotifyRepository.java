package com.ywh.repository;

import com.ywh.entity.OwnerMeetingNotify;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwnerMeetingNotifyRepository extends JpaRepository<OwnerMeetingNotify, Long> {
    Optional<OwnerMeetingNotify> findByMeetingId(Long meetingId);
}
