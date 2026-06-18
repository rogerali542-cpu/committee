package com.ywh.repository;

import com.ywh.entity.OwnerMinutesRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OwnerMinutesRevisionRepository extends JpaRepository<OwnerMinutesRevision, Long> {
    List<OwnerMinutesRevision> findByMeetingIdOrderByVersionNoDesc(Long meetingId);
    Optional<OwnerMinutesRevision> findFirstByMeetingIdOrderByVersionNoDesc(Long meetingId);
}
